package x1.service.registry;

import x1.service.Constants;

public enum Technology {
  REST(Constants.BASE_URI),
  SOAP(Constants.BASE_URI),
  RMI(Constants.JNDI_NAME),
  JMS(Constants.JNDI_NAME), 
  WEBSOCKETS(Constants.BASE_URI), 
  STOMP(Constants.BASE_URI), 
  AMQP(Constants.BASE_URI);

  private Technology(String propertyName) {
    this.propertyName = propertyName;
  }

  public String getPropertyName() {
    return propertyName;
  }

  private String propertyName;
}
