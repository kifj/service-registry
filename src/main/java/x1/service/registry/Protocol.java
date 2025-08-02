package x1.service.registry;

public enum Protocol {
  HTTP("http"),
  HTTPS("https"),
  WS("ws"),
  WSS("wss"),
  EJB("ejb"),
  AMQP("amqp"),
  STOMP_WS("ws"),
  STOMP_WSS("wss"),
  JNP("jnp");
  
  Protocol (String prefix) {
    this.prefix = prefix;
  }
  
  private final String prefix;
  
  public String getPrefix() {
    return prefix;
  }
}
