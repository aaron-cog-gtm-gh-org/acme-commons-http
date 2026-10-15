package com.acme.commons.http;

import org.apache.http.HttpResponse;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.protocol.HttpContext;

final class GetOnlyServiceUnavailableRetryStrategy implements ServiceUnavailableRetryStrategy {
    private final int maxRetries;
    private final long retryIntervalMillis;

    GetOnlyServiceUnavailableRetryStrategy(int maxRetries, long retryIntervalMillis) {
        this.maxRetries = maxRetries;
        this.retryIntervalMillis = retryIntervalMillis;
    }

    @Override
    public boolean retryRequest(HttpResponse response, int executionCount, HttpContext context) {
        if (executionCount > maxRetries || response.getStatusLine().getStatusCode() != 503 || context == null) {
            return false;
        }
        HttpClientContext clientContext = HttpClientContext.adapt(context);
        return clientContext.getRequest() != null
                && "GET".equalsIgnoreCase(clientContext.getRequest().getRequestLine().getMethod());
    }

    @Override
    public long getRetryInterval() {
        return retryIntervalMillis;
    }
}
