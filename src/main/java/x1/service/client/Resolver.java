package x1.service.client;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import x1.service.etcd.EtcdClient;
import x1.service.etcd.Node;
import x1.service.etcd.Result;
import x1.service.registry.Protocol;
import x1.service.registry.Technology;

public class Resolver {
  private static final Logger LOG = LoggerFactory.getLogger(Resolver.class);
  private static final String ETCD_SERVICE = "x1.service.registry.etcd";

  @Inject
  @ConfigProperty(name = ETCD_SERVICE, defaultValue = EtcdClient.DEFAULT_ETCD_SERVICE)
  private URI etcdService;

  @Inject
  @ConfigProperty(name = "x1.service.registry.prefix", defaultValue = "/x1")
  private String prefix;

  public List<Node> resolve(Technology technology, Class<?> serviceClass, String version, String stage,
      Protocol protocol) {
    return resolve(technology, serviceClass.getName(), version, stage, protocol);
  }

  public List<Node> resolve(Technology technology, String serviceClass, String version, String stage,
      Protocol protocol) {
    String directory = getDirectory(technology, serviceClass, version, stage, protocol);
    List<Node> nodes = new ArrayList<>();
    try (EtcdClient etcd = new EtcdClient(etcdService)) {
      Result result = etcd.get(directory);
      if (result != null) {
        nodes = result.getNode().getNodes();
        LOG.trace("get {} -> {}", directory, result);
      }
    } catch (Exception e) {
      LOG.error(null, e);
    }
    return nodes;
  }

  private String getDirectory(Technology technology, String serviceClass, String version, String stage,
      Protocol protocol) {
    return prefix + "/" + technology.name().toLowerCase() + "/" + serviceClass + "/" + version + "/"
        + protocol.name().toLowerCase() + "/" + stage.toLowerCase();
  }

  public Properties getProperties(Node node) {
    Properties props = new Properties();
    try {
      props.load(new StringReader(node.getValue()));
    } catch (IOException e) {
      LOG.warn(e.getMessage());
    }
    return props;
  }
}
