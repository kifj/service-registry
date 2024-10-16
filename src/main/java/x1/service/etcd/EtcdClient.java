package x1.service.etcd;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;

import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriBuilder;

import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

public class EtcdClient implements AutoCloseable {
  public static final String DEFAULT_ETCD_SERVICE = "http://127.0.0.1:4001";
  private static final String PATH_KEYS = "v2/keys";
  private static final Integer ECODE_KEY_NOT_FOUND = 100;
  private final CloseableHttpAsyncClient httpClient = buildDefaultHttpClient();
  private final Gson gson = new GsonBuilder().create();
  private final URI baseUri;

  private static CloseableHttpAsyncClient buildDefaultHttpClient() {
    var requestConfig = RequestConfig.custom().setSocketTimeout(1000).setConnectTimeout(1000)
        .setConnectionRequestTimeout(1000).build();
    var httpClient = HttpAsyncClients.custom().setDefaultRequestConfig(requestConfig).build();
    httpClient.start();
    return httpClient;
  }

  public EtcdClient(URI baseUri) {
    this.baseUri = baseUri;
  }

  /**
   * Retrieves a key. Returns null if not found.
   */
  public Result get(String key) throws ClientException {
    var uri = buildKeyUri(PATH_KEYS, key).build();
    var request = new HttpGet(uri);

    var result = syncExecute(request, new Status[] { Status.OK, Status.NOT_FOUND }, ECODE_KEY_NOT_FOUND);
    if (result.isError() && ECODE_KEY_NOT_FOUND.equals(result.getErrorCode())) {
      return null;
    }
    return result;
  }

  /**
   * Deletes the given key
   */
  public Result delete(String key) throws ClientException {
    var uri = buildKeyUri(PATH_KEYS, key).build();
    var request = new HttpDelete(uri);

    return syncExecute(request, new Status[] { Status.OK, Status.NOT_FOUND });
  }

  /**
   * Sets a key to a new value
   */
  public Result set(String key, String value) throws ClientException {
    return set(key, value, null);
  }

  /**
   * Sets a key to a new value with an (optional) ttl
   */

  public Result set(String key, String value, Integer ttl) throws ClientException {
    List<BasicNameValuePair> data = Lists.newArrayList(new BasicNameValuePair("value", value));
    if (ttl != null) {
      data.add(new BasicNameValuePair("ttl", Integer.toString(ttl)));
    }

    return set0(key, data, new Status[] { Status.OK, Status.CREATED });
  }

  /**
   * Creates a directory
   */
  public Result createDirectory(String key) throws ClientException {
    List<BasicNameValuePair> data = Lists.newArrayList(new BasicNameValuePair("dir", "true"));
    return set0(key, data, new Status[] { Status.OK, Status.CREATED });
  }

  /**
   * Lists a directory
   */
  public List<Node> listDirectory(String key) throws ClientException {
    var result = get(key);
    if (result == null || result.getNode() == null) {
      return Lists.newArrayList();
    }
    return result.getNode().getNodes();
  }

  /**
   * Delete a directory
   */
  public Result deleteDirectory(String key) throws ClientException {
    var uri = buildKeyUri(PATH_KEYS, key).queryParam("dir", "true").build();
    var request = new HttpDelete(uri);
    return syncExecute(request, new Status[] { Status.ACCEPTED });
  }

  /**
   * Sets a key to a new value, if the value is a specified value
   */
  public Result cas(String key, String prevValue, String value) throws ClientException {
    List<BasicNameValuePair> data = Lists.newArrayList(new BasicNameValuePair("value", value),
        new BasicNameValuePair("prevValue", prevValue));
    return set0(key, data, new Status[] { Status.OK, Status.PRECONDITION_FAILED }, 101);
  }

  /**
   * Watches the given subtree
   */
  public ListenableFuture<Result> watch(String key) {
    return watch(key, null, false);
  }

  /**
   * Watches the given subtree
   */
  public ListenableFuture<Result> watch(String key, Long index, boolean recursive) {
    var builder = buildKeyUri(PATH_KEYS, key).queryParam("wait", "true").queryParam("recursive", recursive);
    if (index != null) {
      builder = builder.queryParam("waitIndex", index);
    }
    var uri = builder.build();
    var request = new HttpGet(uri);
    return asyncExecute(request, new Status[] { Status.OK });
  }

  /**
   * Gets the etcd version
   */
  public String version() throws ClientException {
    var uri = baseUri.resolve("/version");

    var request = new HttpGet(uri);

    // Technically not JSON, but it'll work
    // This call is the odd one out
    var s = syncExecuteJson(request, Status.OK);
    if (s.httpStatusCode != Status.OK) {
      throw new ClientException("Error while fetching versions", s.httpStatusCode);
    }
    return s.json;
  }

  private Result set0(String key, List<BasicNameValuePair> data, Status[] httpErrorCodes, Integer... expectedErrorCodes)
      throws ClientException {
    var uri = buildKeyUri(PATH_KEYS, key).build();
    var request = new HttpPut(uri);
    var entity = new UrlEncodedFormEntity(data, Charsets.UTF_8);
    request.setEntity(entity);
    return syncExecute(request, httpErrorCodes, expectedErrorCodes);
  }

  public Result listChildren(String key) throws ClientException {
    var uri = buildKeyUri(PATH_KEYS, key).build();
    var request = new HttpGet(uri);
    return syncExecute(request, new Status[] { Status.OK });
  }

  protected ListenableFuture<Result> asyncExecute(HttpUriRequest request, Status[] expectedHttpStatusCodes,
      final Integer... expectedErrorCodes) {
    var json = asyncExecuteJson(request, expectedHttpStatusCodes);
    return Futures.transformAsync(json, new AsyncFunction<JsonResponse, Result>() {
      public ListenableFuture<Result> apply(JsonResponse json) throws Exception {
        var result = jsonToResult(json, expectedErrorCodes);
        return Futures.immediateFuture(result);
      }
    }, MoreExecutors.directExecutor());
  }

  protected Result syncExecute(HttpUriRequest request, Status[] expectedHttpStatusCodes, Integer... expectedErrorCodes)
      throws ClientException {
    try {
      return asyncExecute(request, expectedHttpStatusCodes, expectedErrorCodes).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ClientException("Interrupted during request", e);
    } catch (ExecutionException e) {
      throw unwrap(e);
    }
  }

  private ClientException unwrap(ExecutionException e) {
    var cause = e.getCause();
    if (cause instanceof ClientException) {
      return (ClientException) cause;
    }
    return new ClientException("Error executing request", e);
  }

  private Result jsonToResult(JsonResponse response, Integer... expectedErrorCodes) throws ClientException {
    if (response == null || response.json == null) {
      return null;
    }
    var result = parseResult(response);

    if (result.isError() && !contains(expectedErrorCodes, result.getErrorCode())) {
      throw new ClientException(result.getMessage(), result);
    }
    return result;
  }

  private Result parseResult(JsonResponse response) throws ClientException {
    Result result;
    try {
      result = gson.fromJson(response.json, Result.class);
    } catch (JsonParseException e) {
      // this is probably plain text
      throw new ClientException("Error parsing response from etcd: " + e.getMessage() + "\n" + response.json,
          response.httpStatusCode);
    }
    return result;
  }

  private static boolean contains(Object[] list, Object find) {
    for (int i = 0; i < list.length; i++) {
      if (list[i] == find) {
        return true;
      }
    }
    return false;
  }

  private JsonResponse syncExecuteJson(HttpUriRequest request, Status... expectedHttpStatusCodes)
      throws ClientException {
    try {
      return asyncExecuteJson(request, expectedHttpStatusCodes).get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ClientException("Interrupted during request processing", e);
    } catch (ExecutionException e) {
      throw unwrap(e);
    }
  }

  private ListenableFuture<JsonResponse> asyncExecuteJson(HttpUriRequest request, Status[] expectedHttpStatusCodes) {
    var response = asyncExecuteHttp(request);

    return Futures.transformAsync(response, new AsyncFunction<HttpResponse, JsonResponse>() {
      public ListenableFuture<JsonResponse> apply(HttpResponse httpResponse) throws Exception {
        var json = extractJsonResponse(httpResponse, expectedHttpStatusCodes);
        return Futures.immediateFuture(json);
      }
    }, MoreExecutors.directExecutor());
  }

  /**
   * We need the status code & the response to parse an error response.
   */
  private static class JsonResponse {
    private String json;
    private Status httpStatusCode;

    public JsonResponse(String json, Status statusCode) {
      this.json = json;
      this.httpStatusCode = statusCode;
    }

  }

  private JsonResponse extractJsonResponse(HttpResponse httpResponse, Status[] expectedHttpStatusCodes)
      throws ClientException {
    try {
      var statusLine = httpResponse.getStatusLine();
      var statusCode = Status.fromStatusCode(statusLine.getStatusCode());

      String json = null;

      if (httpResponse.getEntity() != null) {
        try {
          json = EntityUtils.toString(httpResponse.getEntity());
        } catch (IOException e) {
          throw new ClientException("Error reading response: " + e.getMessage(), statusCode);
        }
      }

      if (!contains(expectedHttpStatusCodes, statusCode)) {
        if (statusCode == Status.BAD_REQUEST && json != null) {
          // More information in JSON
        } else {
          throw new ClientException("Error response from etcd: " + statusLine.getReasonPhrase(), statusCode);
        }
      }

      return new JsonResponse(json, statusCode);
    } finally {
      close(httpResponse);
    }
  }

  private UriBuilder buildKeyUri(String prefix, String key) {
    return UriBuilder.fromUri(baseUri).path(prefix).path(key);
  }

  private ListenableFuture<HttpResponse> asyncExecuteHttp(HttpUriRequest request) {
    final SettableFuture<HttpResponse> future = SettableFuture.create();

    httpClient.execute(request, new FutureCallback<HttpResponse>() {
      public void completed(HttpResponse result) {
        future.set(result);
      }

      public void failed(Exception ex) {
        future.setException(ex);
      }

      public void cancelled() {
        future.setException(new InterruptedException());
      }
    });

    return future;
  }

  private void close(HttpResponse response) {
    if (response == null) {
      return;
    }
    var entity = response.getEntity();
    if (entity != null) {
      EntityUtils.consumeQuietly(entity);
    }
  }

  @Override
  public void close() throws IOException {
    httpClient.close();
  }
}
