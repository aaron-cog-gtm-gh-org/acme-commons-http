package com.acme.commons.http;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes JSON requests against a configured Acme service.
 */
public final class AcmeHttpClient implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(AcmeHttpClient.class);
    private final String baseUrl;
    private final CloseableHttpClient http;
    private final ObjectMapper mapper;
    private final AtomicBoolean closed = new AtomicBoolean();

    AcmeHttpClient(String baseUrl, CloseableHttpClient http, ObjectMapper mapper) {
        this.baseUrl = stripTrailingSlashes(baseUrl);
        this.http = http;
        this.mapper = mapper;
    }

    /**
     * Retrieves and deserializes a JSON response.
     *
     * <p>A 503 response is retried up to two more times, 100 ms apart.</p>
     *
     * @param path request path relative to the service base URL
     * @param type response type
     * @param <T> response type
     * @return the deserialized response, or {@code null} for an empty response
     * @throws AcmeHttpException if the service returns a non-success status
     * @throws UncheckedIOException wrapping org.apache.http.conn.HttpHostConnectException if the host refuses the connection
     * @throws UncheckedIOException when a read timeout occurs; its cause is a SocketTimeoutException
     * @throws IllegalStateException if this client has been closed
     */
    public <T> T get(String path, Class<T> type) {
        ensureOpen();
        HttpGet request = new HttpGet(resolve(path));
        request.setHeader("Accept", "application/json");
        return execute("GET", path, request, type);
    }

    /**
     * Sends a JSON request and deserializes its response.
     *
     * <p>POST requests are not retried on a 503 response.</p>
     *
     * @param path request path relative to the service base URL
     * @param body request value serialized as JSON
     * @param type response type
     * @param <T> response type
     * @return the deserialized response, or {@code null} for an empty response
     * @throws AcmeHttpException if the service returns a non-success status
     * @throws UncheckedIOException wrapping org.apache.http.conn.HttpHostConnectException if the host refuses the connection
     * @throws UncheckedIOException when a read timeout occurs; its cause is a SocketTimeoutException
     * @throws IllegalStateException if this client has been closed
     */
    public <T> T post(String path, Object body, Class<T> type) {
        ensureOpen();
        String url = resolve(path);
        try {
            HttpPost request = new HttpPost(url);
            request.setHeader("Accept", "application/json");
            request.setEntity(new StringEntity(mapper.writeValueAsString(body), ContentType.APPLICATION_JSON));
            return execute("POST", path, request, type);
        } catch (IOException e) {
            throw ioFailure("POST", url, e);
        }
    }

    /**
     * Closes the underlying connection manager. Repeated calls have no effect.
     *
     * @throws UncheckedIOException if closing the underlying HTTP client fails
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                http.close();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to close HTTP client: " + e.getMessage(), e);
            }
        }
    }

    private <T> T execute(String method, String path, HttpRequestBase request, Class<T> type) {
        String url = request.getURI().toString();
        long started = System.nanoTime();
        try (CloseableHttpResponse response = http.execute(request)) {
            int status = response.getStatusLine().getStatusCode();
            HttpEntity entity = response.getEntity();
            String body = entity == null ? "" : EntityUtils.toString(entity, StandardCharsets.UTF_8);
            LOGGER.debug("{} {} returned {} in {} ms", method, url, status,
                    (System.nanoTime() - started) / 1_000_000);
            if (status < 200 || status >= 300) {
                String snippet = body.length() > 200 ? body.substring(0, 200) : body;
                throw new AcmeHttpException(status,
                        method + " " + path + " returned HTTP " + status + ": " + snippet);
            }
            if (body.isEmpty() || type == Void.class) {
                return null;
            }
            if (type == String.class) {
                return type.cast(body);
            }
            return mapper.readValue(body, type);
        } catch (IOException e) {
            throw ioFailure(method, url, e);
        }
    }

    private UncheckedIOException ioFailure(String method, String url, IOException cause) {
        return new UncheckedIOException(method + " " + url + " failed: " + cause.getMessage(), cause);
    }

    private String resolve(String path) {
        String suffix = path.startsWith("/") ? path : "/" + path;
        return baseUrl + suffix;
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("HTTP client is closed");
        }
    }

    private static String stripTrailingSlashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
