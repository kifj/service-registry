package x1.service.registry;

import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.inject.Inject;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.ServletContext;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.commons.lang3.StringUtils;
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
    if (!checkRunningServer()) {
      return;
    }
    URI uri = URI.create(System.getProperty(ETCD_SERVICE, EtcdClient.DEFAULT_ETCD_SERVICE));
    try (EtcdClient etcd = new EtcdClient(uri)) {
      LOG.info("connecting to etcd at {} -> version={}", uri, etcd.version());
    } catch (Exception e) {
      LOG.error("connection failure for etcd at " + uri, e);
    }
    for (String packageName : basePackages) {
      Reflections reflections = new Reflections(packageName);
      reflections.getTypesAnnotatedWith(Service.class).forEach(this::register);
      reflections.getTypesAnnotatedWith(Services.class).forEach(this::registerAll);
    }
  }

  private boolean checkRunningServer() {
    try {
      ObjectName name = new ObjectName("jboss.as:management-root=server");
      String status = (String) mbeanServer.getAttribute(name, "serverState");
      return StringUtils.equalsIgnoreCase(status, "running");
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
    URI uri = URI.create(System.getProperty(ETCD_SERVICE, EtcdClient.DEFAULT_ETCD_SERVICE));
    try (EtcdClient etcd = new EtcdClient(uri)) {
      for (Protocol protocol : service.protocols()) {
        LOG.info("register ({}) at etcd({})", service, uri);
        String directory = getDirectory(serviceClass, service, protocol);
        ensureDirectoryExists(etcd, directory);
        String hostName = getHostName();
        String file = directory + "/" + hostName;
        String value = getValue(service, protocol, hostName);
        Result result = etcd.set(file, value, TTL);
        LOG.debug("set {} to {} -> {}", file, value, result);
      }
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
    String prefix = System.getProperty("x1.service.registry.prefix", "/x1").toLowerCase();
    String stage = System.getProperty("x1.service.registry.stage", "local").toLowerCase();

    return prefix + "/" + service.technology().name().toLowerCase() + "/" + serviceClass.getName() + "/"
        + service.version() + "/" + protocol.name().toLowerCase() + "/" + stage;
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
    URI uri = URI.create(System.getProperty(ETCD_SERVICE, EtcdClient.DEFAULT_ETCD_SERVICE));
    try (EtcdClient etcd = new EtcdClient(uri)) {
      for (Protocol protocol : service.protocols()) {
        LOG.info("unregister ({}) at etcd({})", service, uri);
        String file = getDirectory(serviceClass, service, protocol) + "/" + getHostName();
        Result result = etcd.delete(file);
        LOG.debug("delete {} -> {}", file, result);
      }
    } catch (IOException e) {
      LOG.error(null, e);
    }
  }

  private String getHostName() {
    boolean registerIp = Boolean.getBoolean("x1.service.registry.registerIp");
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
