package x1.service.test;

import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit5.ArquillianExtension;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import x1.service.Constants;
import x1.service.client.Resolver;
import x1.service.etcd.Node;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.UriBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static x1.service.Constants.*;
import static x1.service.registry.Protocol.EJB;
import static x1.service.registry.Protocol.HTTPS;
import static x1.service.registry.Technology.JMS;
import static x1.service.registry.Technology.REST;

@ExtendWith(ArquillianExtension.class)
@DisplayName("Resolver Test")
public class ResolverTest {
  private static final String STAGE = "local";
  static final String APP_VERSION_MAJOR_MINOR = "1.0";
  static final String APP_NAME_MAJOR_MINOR = "test-v" + APP_VERSION_MAJOR_MINOR;
  private String hostname;

  @Inject
  private Resolver resolver;

  @Deployment
  public static Archive<?> createTestArchive() {
    var libraries = Maven.resolver().loadPomFromFile("pom.xml")
        .resolve("org.reflections:reflections", "org.assertj:assertj-core")
        .withTransitivity().asFile();

    return ShrinkWrap.create(WebArchive.class, APP_NAME_MAJOR_MINOR + ".war").addPackages(true, "x1.service")
        .addAsResource("microprofile-config.properties", "META-INF/microprofile-config.properties")
        .addAsResource("service-registry.properties").addAsWebInfResource("beans.xml")
        .addAsWebInfResource("jboss-deployment-structure.xml").addAsLibraries(libraries);
  }

  @BeforeEach
  public void setup() {
    try {
      hostname = InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      hostname = "localhost";
    }
  }

  @Test
  public void testResolveHttps() throws Exception {
    var nodes = resolver.resolve(REST, ShareResource.class, APP_VERSION_MAJOR_MINOR, STAGE, HTTPS);
    assertThat(nodes).size().isPositive();
    var node = getNode(nodes, resolver);
    assertThat(node).isNotNull();
    var props = resolver.getProperties(node);
    var port = 8443;
    var context = "/" + APP_NAME_MAJOR_MINOR;    
    var url = UriBuilder.fromUri("{protocol}://{host}:{port}/{context}").path("/shares").build(HTTPS.getPrefix(),
        hostname, port, context);;
    assertThat(props).containsEntry(BASE_URI, url.toString()).containsEntry(PORT, Integer.toString(port))
        .containsEntry(CONTEXT, context).containsEntry(PROTOCOL, HTTPS.getPrefix()).containsEntry(HOST_NAME, hostname)
        .doesNotContainKeys(Constants.DESTINATION, JNDI_NAME).size().isEqualTo(5);
  }

  @Test
  public void testResolveJms() {
    var nodes = resolver.resolve(JMS, ShareMessageListener.class, APP_VERSION_MAJOR_MINOR, STAGE, EJB);
    assertThat(nodes).size().isPositive();
    var node = getNode(nodes, resolver);
    assertThat(node).isNotNull();
    var props = resolver.getProperties(node);
    var context = "/" + APP_NAME_MAJOR_MINOR;
    var port = 8080;
    assertThat(props).doesNotContainKeys(BASE_URI, Constants.DESTINATION).containsEntry(PORT, Integer.toString(port))
        .containsEntry(CONTEXT, context).containsEntry(PROTOCOL, EJB.getPrefix()).containsEntry(HOST_NAME, hostname)
        .containsEntry(JNDI_NAME, "java:/jms/queue/test").size().isEqualTo(5);
  }

  private Node getNode(List<Node> nodes, Resolver resolver) {
    for (Node node : nodes) {
      var props = resolver.getProperties(node);
      if (props.getProperty(HOST_NAME).equals(hostname)) {
        return node;
      }
    }
    return null;
  }
}
