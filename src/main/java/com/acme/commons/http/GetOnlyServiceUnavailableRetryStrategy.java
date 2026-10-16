package com.acme.commons.http;

import java.io.IOException;

import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;

/**
 * Retries GET requests after HTTP 503 and delegates I/O failure retries to the default
 * HttpClient policy, which only retries idempotent requests and never retries connect
 * failures, timeouts, unknown hosts, or TLS errors.
 */
final class GetOnlyServiceUnavailableRetryStrategy implements HttpRequestRetryStrategy {
    private final int maxRetries;
    private final TimeValue retryInterval;
    private final HttpRequestRetryStrategy ioRetries;

    GetOnlyServiceUnavailableRetryStrategy(int maxRetries, long retryIntervalMillis) {
        this.maxRetries = maxRetries;
        this.retryInterval = TimeValue.ofMilliseconds(retryIntervalMillis);
        this.ioRetries = new DefaultHttpRequestRetryStrategy(maxRetries, TimeValue.ZERO_MILLISECONDS);
    }

    @Override
    public boolean retryRequest(HttpRequest request, IOException exception, int executionCount,
            HttpContext context) {
        return ioRetries.retryRequest(request, exception, executionCount, context);
    }

    @Override
    public boolean retryRequest(HttpResponse response, int executionCount, HttpContext context) {
        if (executionCount > maxRetries || response.getCode() != 503 || context == null) {
            return false;
        }
        HttpRequest request = HttpClientContext.adapt(context).getRequest();
        return request != null && "GET".equalsIgnoreCase(request.getMethod());
    }

    @Override
    public TimeValue getRetryInterval(HttpResponse response, int executionCount, HttpContext context) {
        return retryInterval;
    }
}
