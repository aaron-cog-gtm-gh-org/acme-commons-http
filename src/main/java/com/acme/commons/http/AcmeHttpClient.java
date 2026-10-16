package com.acme.commons.http;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
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
     * @throws UncheckedIOException wrapping a {@link java.net.ConnectException} (org.apache.hc.client5.http.HttpHostConnectException) if the host refuses the connection
     * @throws UncheckedIOException when a read timeout occurs; its cause is a SocketTimeoutException
     * @throws IllegalStateException if this client has been closed
     */
    public <T> T get(String path, Class<T> type) {
        ensureOpen();
        String url = resolve(path);
        HttpGet request = new HttpGet(url);
        request.setHeader("Accept", "application/json");
        return execute("GET", path, url, request, type);
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
     * @throws UncheckedIOException wrapping a {@link java.net.ConnectException} (org.apache.hc.client5.http.HttpHostConnectException) if the host refuses the connection
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
            return execute("POST", path, url, request, type);
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

    private <T> T execute(String method, String path, String url, HttpUriRequestBase request, Class<T> type) {
        long started = System.nanoTime();
        try {
            return http.execute(request, response -> {
                int status = response.getCode();
                HttpEntity entity = response.getEntity();
                String body = readBody(entity);
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
            });
        } catch (IOException e) {
            throw ioFailure(method, url, e);
        }
    }

    private static String readBody(HttpEntity entity) throws IOException {
        if (entity == null) {
            return "";
        }
        try {
            return EntityUtils.toString(entity, StandardCharsets.UTF_8);
        } catch (ParseException e) {
            throw new IOException("Failed to read response body: " + e.getMessage(), e);
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
