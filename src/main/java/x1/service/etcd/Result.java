package x1.service.etcd;

public class Result {
  // General values
  private String action;
  private Node node;
  private Node prevNode;

  // For errors
  private Integer errorCode;
  private String message;
  private String cause;
  private int errorIndex;

  public boolean isError() {
    return errorCode != null;
  }

  /**
   * @return the action
   */
  public String getAction() {
    return action;
  }

  /**
   * @param action
   *          the action to set
   */
  public void setAction(String action) {
    this.action = action;
  }

  /**
   * @return the node
   */
  public Node getNode() {
    return node;
  }

  /**
   * @param node
   *          the node to set
   */
  public void setNode(Node node) {
    this.node = node;
  }

  /**
   * @return the prevNode
   */
  public Node getPrevNode() {
    return prevNode;
  }

  /**
   * @param prevNode
   *          the prevNode to set
   */
  public void setPrevNode(Node prevNode) {
    this.prevNode = prevNode;
  }

  /**
   * @return the errorCode
   */
  public Integer getErrorCode() {
    return errorCode;
  }

  /**
   * @param errorCode
   *          the errorCode to set
   */
  public void setErrorCode(Integer errorCode) {
    this.errorCode = errorCode;
  }

  /**
   * @return the message
   */
  public String getMessage() {
    return message;
  }

  /**
   * @param message
   *          the message to set
   */
  public void setMessage(String message) {
    this.message = message;
  }

  /**
   * @return the cause
   */
  public String getCause() {
    return cause;
  }

  /**
   * @param cause
   *          the cause to set
   */
  public void setCause(String cause) {
    this.cause = cause;
  }

  /**
   * @return the errorIndex
   */
  public int getErrorIndex() {
    return errorIndex;
  }

  /**
   * @param errorIndex
   *          the errorIndex to set
   */
  public void setErrorIndex(int errorIndex) {
    this.errorIndex = errorIndex;
  }

  /* (non-Javadoc)
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("Result [action=");
    builder.append(action);
    builder.append(", node=");
    builder.append(node);
    builder.append(", prevNode=");
    builder.append(prevNode);
    builder.append(", errorCode=");
    builder.append(errorCode);
    builder.append(", message=");
    builder.append(message);
    builder.append(", cause=");
    builder.append(cause);
    builder.append(", errorIndex=");
    builder.append(errorIndex);
    builder.append("]");
    return builder.toString();
  }

}