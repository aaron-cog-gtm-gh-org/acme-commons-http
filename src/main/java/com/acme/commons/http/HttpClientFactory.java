package com.acme.commons.http;

import java.time.Duration;
import java.util.Objects;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

/**
 * Creates configured HTTP clients for Acme service integrations.
 */
public final class HttpClientFactory {
    static final String USER_AGENT = "acme-commons-http/1.5.0";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RETRIES = 2;
    private static final TimeValue SERVICE_UNAVAILABLE_RETRY_INTERVAL = TimeValue.ofMilliseconds(100);
    private static final TimeValue VALIDATE_AFTER_INACTIVITY = TimeValue.ofSeconds(2);
    private static final int MAX_CONNECTIONS_PER_ROUTE = 2;
    private static final int MAX_CONNECTIONS_TOTAL = 20;
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
        Timeout ms = Timeout.ofMilliseconds(Math.toIntExact(timeout.toMillis()));
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(ms)
                        .setSocketTimeout(ms)
                        .setValidateAfterInactivity(VALIDATE_AFTER_INACTIVITY)
                        .build())
                .setMaxConnPerRoute(MAX_CONNECTIONS_PER_ROUTE)
                .setMaxConnTotal(MAX_CONNECTIONS_TOTAL)
                .build();
        RequestConfig cfg = RequestConfig.custom()
                .setConnectionRequestTimeout(ms)
                .setResponseTimeout(ms)
                .build();
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(cfg)
                .setRetryStrategy(new AcmeRetryStrategy(MAX_RETRIES, SERVICE_UNAVAILABLE_RETRY_INTERVAL))
                .setRedirectStrategy(new SafeMethodRedirectStrategy())
                .setUserAgent(USER_AGENT)
                .build();
        return new AcmeHttpClient(baseUrl, httpClient, MAPPER);
    }
}
