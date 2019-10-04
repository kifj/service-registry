package x1.service.registry;

import javax.ejb.EJB;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class DeploymentListener implements ServletContextListener {
  @EJB
  private ServiceRegistrator registrator;

  @Override
  public void contextDestroyed(ServletContextEvent e) {
    registrator.stop();
  }

  @Override
  public void contextInitialized(ServletContextEvent e) {
    registrator.update();
  }

}
