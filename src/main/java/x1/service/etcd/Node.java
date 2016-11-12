package x1.service.etcd;

import java.util.ArrayList;
import java.util.List;

public class Node {
  private String key;
  private long createdIndex;
  private long modifiedIndex;
  private String value;

  // For TTL keys
  private String expiration;
  private Integer ttl;

  // For listings
  private boolean dir;
  private List<Node> nodes = new ArrayList<>();

  /**
   * @return the key
   */
  public String getKey() {
    return key;
  }

  /**
   * @param key
   *          the key to set
   */
  public void setKey(String key) {
    this.key = key;
  }

  /**
   * @return the createdIndex
   */
  public long getCreatedIndex() {
    return createdIndex;
  }

  /**
   * @param createdIndex
   *          the createdIndex to set
   */
  public void setCreatedIndex(long createdIndex) {
    this.createdIndex = createdIndex;
  }

  /**
   * @return the modifiedIndex
   */
  public long getModifiedIndex() {
    return modifiedIndex;
  }

  /**
   * @param modifiedIndex
   *          the modifiedIndex to set
   */
  public void setModifiedIndex(long modifiedIndex) {
    this.modifiedIndex = modifiedIndex;
  }

  /**
   * @return the value
   */
  public String getValue() {
    return value;
  }

  /**
   * @param value
   *          the value to set
   */
  public void setValue(String value) {
    this.value = value;
  }

  /**
   * @return the expiration
   */
  public String getExpiration() {
    return expiration;
  }

  /**
   * @param expiration
   *          the expiration to set
   */
  public void setExpiration(String expiration) {
    this.expiration = expiration;
  }

  /**
   * @return the ttl
   */
  public Integer getTtl() {
    return ttl;
  }

  /**
   * @param ttl
   *          the ttl to set
   */
  public void setTtl(Integer ttl) {
    this.ttl = ttl;
  }

  /**
   * @return the dir
   */
  public boolean isDir() {
    return dir;
  }

  /**
   * @param dir
   *          the dir to set
   */
  public void setDir(boolean dir) {
    this.dir = dir;
  }

  /**
   * @return the nodes
   */
  public List<Node> getNodes() {
    return nodes;
  }

  /**
   * @param nodes
   *          the nodes to set
   */
  public void setNodes(List<Node> nodes) {
    this.nodes = nodes;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("Node [key=");
    builder.append(key);
    builder.append(", createdIndex=");
    builder.append(createdIndex);
    builder.append(", modifiedIndex=");
    builder.append(modifiedIndex);
    builder.append(", value=");
    builder.append(value);
    builder.append(", expiration=");
    builder.append(expiration);
    builder.append(", ttl=");
    builder.append(ttl);
    builder.append(", dir=");
    builder.append(dir);
    builder.append(", nodes=");
    builder.append(nodes);
    builder.append("]");
    return builder.toString();
  }

}
