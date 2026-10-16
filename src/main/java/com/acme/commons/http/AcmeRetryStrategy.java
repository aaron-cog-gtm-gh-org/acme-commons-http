package com.acme.commons.http;

import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;

/**
 * Retries I/O failures of idempotent requests, and HTTP 503 responses to GET requests only,
 * at a fixed interval.
 */
final class AcmeRetryStrategy extends DefaultHttpRequestRetryStrategy {
    private final int maxRetries;
    private final TimeValue retryInterval;

    AcmeRetryStrategy(int maxRetries, TimeValue retryInterval) {
        super(maxRetries, retryInterval);
        this.maxRetries = maxRetries;
        this.retryInterval = retryInterval;
    }

    @Override
    public boolean retryRequest(HttpResponse response, int execCount, HttpContext context) {
        if (execCount > maxRetries || response.getCode() != HttpStatus.SC_SERVICE_UNAVAILABLE || context == null) {
            return false;
        }
        HttpRequest request = HttpClientContext.adapt(context).getRequest();
        return request != null && "GET".equalsIgnoreCase(request.getMethod());
    }

    @Override
    public TimeValue getRetryInterval(HttpResponse response, int execCount, HttpContext context) {
        return retryInterval;
    }
}
