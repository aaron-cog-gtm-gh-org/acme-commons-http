package com.acme.commons.http;

import java.time.Duration;
import java.util.Objects;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;

/**
 * Creates configured HTTP clients for Acme service integrations.
 */
public final class HttpClientFactory {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private HttpClientFactory() {
    }

    /**
     * Creates a client with a five-second connect and socket timeout.
     *
     * @param baseUrl service base URL
     * @return a client configured for the service
     * @throws IllegalArgumentException if the base URL is blank
     */
    public static AcmeHttpClient create(String baseUrl) {
        return create(baseUrl, DEFAULT_TIMEOUT);
    }

    /**
     * Creates a client with the supplied connect and socket timeout.
     *
     * @param baseUrl service base URL
     * @param timeout positive timeout applied to connection acquisition, connect, and socket reads
     * @return a client configured for the service
     * @throws IllegalArgumentException if the base URL is blank or timeout is not positive
     * @throws ArithmeticException if the timeout cannot be represented in milliseconds
     */
    public static AcmeHttpClient create(String baseUrl, Duration timeout) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl must not be blank");
        }
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        int ms = Math.toIntExact(timeout.toMillis());
        RequestConfig cfg = RequestConfig.custom()
                .setConnectTimeout(ms)
                .setSocketTimeout(ms)
                .setConnectionRequestTimeout(ms)
                .build();
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(cfg)
                .setRetryHandler(new DefaultHttpRequestRetryHandler(2, false))
                .setServiceUnavailableRetryStrategy(new GetOnlyServiceUnavailableRetryStrategy(2, 100))
                .setUserAgent("acme-commons-http/1.4.0")
                .build();
        return new AcmeHttpClient(baseUrl, httpClient, MAPPER);
    }
}
