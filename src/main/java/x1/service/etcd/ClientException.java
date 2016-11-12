package x1.service.etcd;

import java.io.IOException;

import javax.ws.rs.core.Response.Status;

public class ClientException extends IOException {
  private static final long serialVersionUID = 1L;

  private final Status httpStatusCode;

  private final Result result;

  public ClientException(String message, Throwable cause) {
    super(message, cause);
    this.httpStatusCode = null;
    this.result = null;
  }

  public ClientException(String message, Status statusCode) {
    super(message + "(" + statusCode + ")");
    this.httpStatusCode = statusCode;
    this.result = null;
  }

  public ClientException(String message, Result result) {
    super(message);
    this.httpStatusCode = null;
    this.result = result;
  }

  public Status getHttpStatusCode() {
    return httpStatusCode;
  }

  public Result getResult() {
    return result;
  }

  public boolean isEtcdError(Integer etcdCode) {
    return result != null && result.getErrorCode() != null && etcdCode.equals(result.getErrorCode());
  }
}
