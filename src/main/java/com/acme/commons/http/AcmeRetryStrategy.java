package com.acme.commons.http;

import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.List;

import javax.net.ssl.SSLException;

import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;

/**
 * Retries idempotent requests after recoverable I/O failures, immediately, and GET requests after a 503
 * response at a fixed interval. Other statuses, including 429, are never retried and {@code Retry-After}
 * is ignored.
 */
final class AcmeRetryStrategy extends DefaultHttpRequestRetryStrategy {
    private final int maxRetries;
    private final TimeValue serviceUnavailableInterval;

    AcmeRetryStrategy(int maxRetries, TimeValue serviceUnavailableInterval) {
        super(maxRetries, TimeValue.ZERO_MILLISECONDS,
                List.of(InterruptedIOException.class, UnknownHostException.class, ConnectException.class,
                        SSLException.class),
                List.of());
        this.maxRetries = maxRetries;
        this.serviceUnavailableInterval = serviceUnavailableInterval;
    }

    @Override
    public boolean retryRequest(HttpResponse response, int execCount, HttpContext context) {
        if (execCount > maxRetries || response.getCode() != HttpStatus.SC_SERVICE_UNAVAILABLE || context == null) {
            return false;
        }
        HttpRequest request = HttpClientContext.adapt(context).getRequest();
        return request != null && Method.GET.isSame(request.getMethod());
    }

    @Override
    public TimeValue getRetryInterval(HttpResponse response, int execCount, HttpContext context) {
        return serviceUnavailableInterval;
    }
}
