package x1.service.registry;

import jakarta.ejb.Schedule;
import jakarta.ejb.Singleton;
import jakarta.inject.Inject;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import jakarta.servlet.ServletContext;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Properties;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import x1.service.etcd.ClientException;
import x1.service.etcd.EtcdClient;
import x1.service.etcd.Result;
import static x1.service.Constants.*;

@Singleton
public class ServiceRegistrator {
  private static final Logger LOG = LoggerFactory.getLogger(ServiceRegistrator.class);
  private static final Integer TTL = 5 * 60;
  private static final String ETCD_SERVICE = "x1.service.registry.etcd";

  private MBeanServer mbeanServer;
  private Properties properties = new Properties();
  private String[] basePackages;
  private boolean stopped;

  @Inject
  @ConfigProperty(name = ETCD_SERVICE, defaultValue = EtcdClient.DEFAULT_ETCD_SERVICE)
  private URI etcdService;

  @Inject
  @ConfigProperty(name = "x1.service.registry.registerIp", defaultValue = "false")
  private boolean registerIp;

  @Inject
  @ConfigProperty(name = "x1.service.registry.prefix", defaultValue = "/x1")
  private String prefix;

  @Inject
  @ConfigProperty(name = "x1.service.registry.stage", defaultValue = "local")
  private String stage;

  @Inject
  @ConfigProperty(name = "x1.service.registry.enabled", defaultValue = "true")
  private boolean enabled;

  @Inject
  private ServletContext context;

  @PostConstruct
  public void init() {
    mbeanServer = ManagementFactory.getPlatformMBeanServer();
    try (InputStream is = this.getClass().getClassLoader().getResourceAsStream("/service-registry.properties")) {
      if (is != null) {
        LOG.debug("Loading service-registry.properties");
        properties.load(is);
        basePackages = StringUtils.split(properties.getProperty("basePackages", "com"), ",");
      }
    } catch (IOException e) {
      LOG.warn(e.getMessage());
      basePackages = new String[0];
    }
  }

  @Schedule(hour = "*", minute = "*/5", second = "0", persistent = false)
  public void update() {
    if (!checkRunningServer() || !enabled) {
      return;
    }
    try (EtcdClient etcd = new EtcdClient(etcdService)) {
      LOG.info("connecting to etcd at {} -> version={}", etcdService, etcd.version());
    } catch (Exception e) {
      LOG.error("connection failure for etcd at " + etcdService, e);
    }
    LOG.info("Scanning base packages {}", Arrays.toString(basePackages));
    for (String packageName : basePackages) {
      Reflections reflections = new Reflections(packageName);
      reflections.getTypesAnnotatedWith(Service.class).forEach(this::register);
      reflections.getTypesAnnotatedWith(Services.class).forEach(this::registerAll);
    }
  }

  public void stop() {
    stopped = true;
  }

  private boolean checkRunningServer() {
    try {
      ObjectName name = new ObjectName("jboss.as:management-root=server");
      String status = (String) mbeanServer.getAttribute(name, "serverState");
      return Arrays.asList("running", "reload-required", "restart-required").contains(status.toLowerCase());
    } catch (Exception e) {
      LOG.warn(e.getMessage());
      return false;
    }
  }

  @PreDestroy
  public void destroy() {
    for (String packageName : basePackages) {
      Reflections reflections = new Reflections(packageName);
      reflections.getTypesAnnotatedWith(Service.class).forEach(this::unregister);
      reflections.getTypesAnnotatedWith(Services.class).forEach(this::unregisterAll);
    }
  }

  private void registerAll(Class<?> serviceClass) {
    for (Service service : serviceClass.getAnnotation(Services.class).services()) {
      register(serviceClass, service);
    }
  }

  private void register(Class<?> serviceClass) {
    register(serviceClass, serviceClass.getAnnotation(Service.class));
  }

  private void register(Class<?> serviceClass, Service service) {
    try (EtcdClient etcd = new EtcdClient(etcdService)) {
      for (Protocol protocol : service.protocols()) {
        if (stopped) {
          break;
        }
        LOG.info("register ({}) at etcd({})", service, etcdService);
        String directory = getDirectory(serviceClass, service, protocol);
        ensureDirectoryExists(etcd, directory);
        String hostName = getHostName();
        String file = directory + "/" + hostName;
        String value = getValue(service, protocol, hostName);
        Result result = etcd.set(file, value, TTL);
        LOG.debug("set {} to {} -> {}", file, value, result);
      }
    } catch (ClientException e) {
      LOG.warn(e.getMessage());
    } catch (Exception e) {
      LOG.error(null, e);
    }
  }

  private void ensureDirectoryExists(EtcdClient etcd, String path) throws ClientException {
    Result result = etcd.get(path);
    if (result == null) {
      result = etcd.createDirectory(path);
      LOG.debug("create directory {} -> {}", path, result);
    } else if (!result.getNode().isDir()) {
      result = etcd.delete(path);
      result = etcd.createDirectory(path);
      LOG.debug("delete and recreate directory {} -> {}", path, result);
    }
  }

  private String getDirectory(Class<?> serviceClass, Service service, Protocol protocol) {
    return prefix + "/" + service.technology().name().toLowerCase() + "/" + serviceClass.getName() + "/"
        + service.version() + "/" + protocol.name().toLowerCase() + "/" + stage.toLowerCase();
  }

  private String getValue(Service service, Protocol protocol, String hostName) {
    StringBuilder sb = new StringBuilder();
    addLine(sb, HOST_NAME, hostName);
    Integer port = getPort(protocol);
    if (port != null) {
      addLine(sb, PORT, port.toString());
    }
    if (protocol != null) {
      addLine(sb, PROTOCOL, protocol.getPrefix());
    }
    String contextPath = getContext(service, context);
    addLine(sb, CONTEXT, contextPath);
    addLine(sb, service.technology().getPropertyName(),
        getServiceValue(service, protocol, hostName, port, contextPath));
    addLine(sb, DESTINATION, getDestination(service));
    return sb.toString();
  }

  private String getServiceValue(Service service, Protocol protocol, String hostName, Integer port,
      String contextPath) {
    String address = hostName;
    if (port != null) {
      address += ":" + port;
    }
    switch (service.technology()) {
    case SOAP:
    case REST:
    case AMQP:
    case WEBSOCKETS:
      return protocol.getPrefix() + "://" + address + contextPath + service.value();
    case STOMP:
      return protocol.getPrefix() + "://" + address + contextPath;
    default:
      return service.value();
    }
  }

  private String getDestination(Service service) {
    switch (service.technology()) {
    case STOMP:
      return service.value();
    default:
      return null;
    }
  }

  private String getContext(Service service, ServletContext context) {
    switch (service.technology()) {
    case STOMP:
      // pre-defined in JBoss
      return "/stomp";
    default:
      return context.getContextPath();
    }
  }

  private StringBuilder addLine(StringBuilder sb, String key, String value) {
    if (value != null) {
      sb.append(key).append("=").append(value).append("\n");
    }
    return sb;
  }

  private void unregisterAll(Class<?> serviceClass) {
    for (Service service : serviceClass.getAnnotation(Services.class).services()) {
      unregister(serviceClass, service);
    }
  }

  private void unregister(Class<?> serviceClass) {
    unregister(serviceClass, serviceClass.getAnnotation(Service.class));
  }

  private void unregister(Class<?> serviceClass, Service service) {
    if (enabled) {
      try (EtcdClient etcd = new EtcdClient(etcdService)) {
        for (Protocol protocol : service.protocols()) {
          LOG.info("unregister ({}) at etcd({})", service, etcdService);
          String file = getDirectory(serviceClass, service, protocol) + "/" + getHostName();
          unregister(etcd, file);
        }
      } catch (IOException e) {
        LOG.error(null, e);
      }
    }
  }

  private void unregister(EtcdClient etcd, String file) {
    try {
      Result result = etcd.delete(file);
      LOG.debug("delete {} -> {}", file, result);
    } catch (ClientException e) {
      LOG.warn(e.getMessage());
    }
  }

  private String getHostName() {
    if (registerIp) {
      try {
        return InetAddress.getLocalHost().getHostAddress();
      } catch (UnknownHostException e) {
        return "127.0.0.1";
      }
    } else {
      try {
        return InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException e) {
        return "localhost";
      }
    }
  }

  private Integer getPort(Protocol protocol) {
    try {
      ObjectName name;
      switch (protocol) {
      case HTTP:
      case WS:
      case EJB:
        name = new ObjectName("jboss.as:socket-binding-group=standard-sockets,socket-binding=http");
        break;
      case HTTPS:
      case WSS:
        name = new ObjectName("jboss.as:socket-binding-group=standard-sockets,socket-binding=https");
        break;
      case STOMP_WS:
      case STOMP_WSS:
        name = new ObjectName("jboss.as:socket-binding-group=standard-sockets,socket-binding=messaging-stomp");
        break;
      case AMQP:
        name = new ObjectName("jboss.as:socket-binding-group=standard-sockets,socket-binding=messaging-amqp");
        break;
      default:
        return null;
      }
      return (Integer) mbeanServer.getAttribute(name, "port");
    } catch (Exception e) {
      return null;
    }
  }

}
