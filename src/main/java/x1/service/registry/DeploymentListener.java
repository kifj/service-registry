package x1.service.registry;

import jakarta.ejb.EJB;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebListener
public class DeploymentListener implements ServletContextListener {
  private static final Logger LOG = LoggerFactory.getLogger(DeploymentListener.class);

  @EJB
  private ServiceRegistrator registrator;

  @Override
  public void contextDestroyed(ServletContextEvent e) {
    try {
      registrator.stop();
    } catch (Exception ex) {
      LOG.error(e.getServletContext().getContextPath(), ex);
    }
  }

  @Override
  public void contextInitialized(ServletContextEvent e) {
    try {
      registrator.update();
    } catch (Exception ex) {
      LOG.error(e.getServletContext().getContextPath(), ex);
    }
  }

}
