package com.acme.commons.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class AcmeHttpClientTest {
    @RegisterExtension
    static final WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Test
    void getsJsonAndSendsAcceptHeader() {
        wireMock.stubFor(get(urlEqualTo("/v1/accounts/42"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"42\",\"balance\":125.50}")));

        try (AcmeHttpClient client = HttpClientFactory.create(wireMock.baseUrl())) {
            Account account = client.get("/v1/accounts/42", Account.class);
            assertThat(account).isEqualTo(new Account("42", 125.50));
        }

        wireMock.verify(getRequestedFor(urlEqualTo("/v1/accounts/42"))
                .withHeader("Accept", equalTo("application/json")));
    }

    @Test
    void postsJsonAndMapsResponse() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments"))
                .withRequestBody(equalToJson("{\"accountId\":\"42\",\"amount\":20.00}"))
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"p-9\",\"balance\":105.50}")));

        try (AcmeHttpClient client = HttpClientFactory.create(wireMock.baseUrl())) {
            Account result = client.post("/v1/payments", new Payment("42", 20.00), Account.class);
            assertThat(result).isEqualTo(new Account("p-9", 105.50));
        }

        wireMock.verify(postRequestedFor(urlEqualTo("/v1/payments"))
                .withHeader("Content-Type", containing("application/json")));
    }

    @Test
    void mapsNotFoundToAcmeHttpException() {
        wireMock.stubFor(get(urlEqualTo("/missing")).willReturn(aResponse().withStatus(404)));

        try (AcmeHttpClient client = HttpClientFactory.create(wireMock.baseUrl())) {
            assertThatThrownBy(() -> client.get("/missing", Account.class))
                    .isInstanceOf(AcmeHttpException.class)
                    .satisfies(error -> assertThat(((AcmeHttpException) error).status()).isEqualTo(404));
        }
    }

    @Test
    void mapsServerErrorToAcmeHttpException() {
        wireMock.stubFor(get(urlEqualTo("/unavailable")).willReturn(aResponse().withStatus(500)));

        try (AcmeHttpClient client = HttpClientFactory.create(wireMock.baseUrl())) {
            assertThatThrownBy(() -> client.get("/unavailable", Account.class))
                    .isInstanceOf(AcmeHttpException.class)
                    .satisfies(error -> assertThat(((AcmeHttpException) error).status()).isEqualTo(500));
        }
    }

    @Test
    void wrapsReadTimeoutWithSocketTimeoutCause() {
        wireMock.stubFor(get(urlEqualTo("/slow")).willReturn(aResponse().withFixedDelay(3_000)));

        try (AcmeHttpClient client = HttpClientFactory.create(wireMock.baseUrl(), Duration.ofMillis(500))) {
            long started = System.nanoTime();
            assertThatThrownBy(() -> client.get("/slow", Account.class))
                    .isInstanceOf(UncheckedIOException.class)
                    .hasCauseInstanceOf(SocketTimeoutException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started).toMillis()).isLessThan(2_000);
        }
    }

    @Test
    void retriesGet503AndThenSucceeds() {
        wireMock.stubFor(get(urlEqualTo("/retry"))
                .inScenario("temporary outage")
                .whenScenarioStateIs(Scenario.STARTED)
                .willSetStateTo("recovered")
                .willReturn(aResponse().withStatus(503)));
        wireMock.stubFor(get(urlEqualTo("/retry"))
                .inScenario("temporary outage")
                .whenScenarioStateIs("recovered")
                .willReturn(aResponse().withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"42\",\"balance\":10.0}")));

        try (AcmeHttpClient client = HttpClientFactory.create(wireMock.baseUrl())) {
            assertThat(client.get("/retry", Account.class).id()).isEqualTo("42");
        }

        wireMock.verify(2, getRequestedFor(urlEqualTo("/retry")));
    }

    @Test
    void exhaustsTwoGetRetriesOn503() {
        wireMock.stubFor(get(urlEqualTo("/always-unavailable")).willReturn(aResponse().withStatus(503)));

        try (AcmeHttpClient client = HttpClientFactory.create(wireMock.baseUrl())) {
            assertThatThrownBy(() -> client.get("/always-unavailable", Account.class))
                    .isInstanceOf(AcmeHttpException.class)
                    .satisfies(error -> assertThat(((AcmeHttpException) error).status()).isEqualTo(503));
        }

        wireMock.verify(3, getRequestedFor(urlEqualTo("/always-unavailable")));
    }

    @Test
    void doesNotRetryPost503() {
        wireMock.stubFor(post(urlEqualTo("/payments")).willReturn(aResponse().withStatus(503)));

        try (AcmeHttpClient client = HttpClientFactory.create(wireMock.baseUrl())) {
            assertThatThrownBy(() -> client.post("/payments", new Payment("42", 20.00), Account.class))
                    .isInstanceOf(AcmeHttpException.class)
                    .satisfies(error -> assertThat(((AcmeHttpException) error).status()).isEqualTo(503));
        }

        wireMock.verify(1, postRequestedFor(urlEqualTo("/payments")));
    }

    @Test
    void wrapsConnectionRefusal() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        try (AcmeHttpClient client = HttpClientFactory.create("http://127.0.0.1:" + port)) {
            assertThatThrownBy(() -> client.get("/refused", Account.class))
                    .isInstanceOf(UncheckedIOException.class)
                    .hasCauseInstanceOf(ConnectException.class);
        }
    }

    @Test
    void closeIsIdempotentAndClientCannotBeReused() {
        AcmeHttpClient client = HttpClientFactory.create(wireMock.baseUrl());
        client.close();
        client.close();

        assertThatThrownBy(() -> client.get("/accounts/42", Account.class))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void defaultTimeoutFactoryCreatesAWorkingClient() {
        wireMock.stubFor(get(urlEqualTo("/health")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"healthy\",\"balance\":0}")));

        try (AcmeHttpClient client = HttpClientFactory.create(wireMock.baseUrl())) {
            assertThat(client.get("/health", Account.class).id()).isEqualTo("healthy");
        }
    }

    @Test
    void doesNotRetryGet429() {
        wireMock.stubFor(get(urlEqualTo("/throttled")).willReturn(aResponse().withStatus(429)
                .withHeader("Retry-After", "1")));

        try (AcmeHttpClient client = HttpClientFactory.create(wireMock.baseUrl())) {
            assertThatThrownBy(() -> client.get("/throttled", Account.class))
                    .isInstanceOfSatisfying(AcmeHttpException.class, error -> assertThat(error.status()).isEqualTo(429));
        }

        wireMock.verify(1, getRequestedFor(urlEqualTo("/throttled")));
    }

    @Test
    void ignoresRetryAfterOnGet503() {
        wireMock.stubFor(get(urlEqualTo("/retry-after")).willReturn(aResponse().withStatus(503)
                .withHeader("Retry-After", "30")));

        try (AcmeHttpClient client = HttpClientFactory.create(wireMock.baseUrl())) {
            long started = System.nanoTime();
            assertThatThrownBy(() -> client.get("/retry-after", Account.class))
                    .isInstanceOf(AcmeHttpException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - started).toMillis()).isBetween(180L, 2_000L);
        }

        wireMock.verify(3, getRequestedFor(urlEqualTo("/retry-after")));
    }

    @Test
    void truncatesErrorBodyTo200Characters() {
        String body = "x".repeat(150) + "y".repeat(150);
        wireMock.stubFor(get(urlEqualTo("/invalid")).willReturn(aResponse().withStatus(422).withBody(body)));

        try (AcmeHttpClient client = HttpClientFactory.create(wireMock.baseUrl())) {
            assertThatThrownBy(() -> client.get("/invalid", Account.class))
                    .isInstanceOfSatisfying(AcmeHttpException.class, error -> {
                        assertThat(error.status()).isEqualTo(422);
                        assertThat(error.getMessage())
                                .isEqualTo("GET /invalid returned HTTP 422: " + body.substring(0, 200));
                    });
        }
    }

    @Test
    void sendsLibraryUserAgent() {
        wireMock.stubFor(get(urlEqualTo("/agent")).willReturn(aResponse().withBody("ok")));

        try (AcmeHttpClient client = HttpClientFactory.create(wireMock.baseUrl())) {
            assertThat(client.get("/agent", String.class)).isEqualTo("ok");
        }

        wireMock.verify(getRequestedFor(urlEqualTo("/agent"))
                .withHeader("User-Agent", equalTo("acme-commons-http/1.5.0")));
    }

    @Test
    void followsGetRedirect() {
        wireMock.stubFor(get(urlEqualTo("/old")).willReturn(aResponse().withStatus(302)
                .withHeader("Location", "/new")));
        wireMock.stubFor(get(urlEqualTo("/new")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"42\",\"balance\":1.0}")));

        try (AcmeHttpClient client = HttpClientFactory.create(wireMock.baseUrl())) {
            assertThat(client.get("/old", Account.class).id()).isEqualTo("42");
        }

        wireMock.verify(getRequestedFor(urlEqualTo("/new")).withHeader("Accept", equalTo("application/json")));
    }

    @Test
    void doesNotFollowPost302Or307() {
        wireMock.stubFor(post(urlEqualTo("/found")).willReturn(aResponse().withStatus(302)
                .withHeader("Location", "/elsewhere")));
        wireMock.stubFor(post(urlEqualTo("/temporary")).willReturn(aResponse().withStatus(307)
                .withHeader("Location", "/elsewhere")));

        try (AcmeHttpClient client = HttpClientFactory.create(wireMock.baseUrl())) {
            assertThatThrownBy(() -> client.post("/found", new Payment("42", 1.00), Account.class))
                    .isInstanceOfSatisfying(AcmeHttpException.class, error -> assertThat(error.status()).isEqualTo(302));
            assertThatThrownBy(() -> client.post("/temporary", new Payment("42", 1.00), Account.class))
                    .isInstanceOfSatisfying(AcmeHttpException.class, error -> assertThat(error.status()).isEqualTo(307));
        }

        wireMock.verify(1, postRequestedFor(urlEqualTo("/found")));
        wireMock.verify(1, postRequestedFor(urlEqualTo("/temporary")));
        wireMock.verify(0, getRequestedFor(urlEqualTo("/elsewhere")));
        wireMock.verify(0, postRequestedFor(urlEqualTo("/elsewhere")));
    }

    @Test
    void followsPost303AsGet() {
        wireMock.stubFor(post(urlEqualTo("/submit")).willReturn(aResponse().withStatus(303)
                .withHeader("Location", "/result")));
        wireMock.stubFor(get(urlEqualTo("/result")).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\":\"r-1\",\"balance\":0}")));

        try (AcmeHttpClient client = HttpClientFactory.create(wireMock.baseUrl())) {
            assertThat(client.post("/submit", new Payment("42", 1.00), Account.class).id()).isEqualTo("r-1");
        }

        wireMock.verify(1, postRequestedFor(urlEqualTo("/submit")));
        wireMock.verify(1, getRequestedFor(urlEqualTo("/result")));
    }

    @Test
    void wrapsPostConnectionRefusal() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        try (AcmeHttpClient client = HttpClientFactory.create("http://127.0.0.1:" + port)) {
            assertThatThrownBy(() -> client.post("/refused", new Payment("42", 1.00), Account.class))
                    .isInstanceOf(UncheckedIOException.class)
                    .hasCauseInstanceOf(ConnectException.class);
        }
    }

    @Test
    void doesNotRetryPostReadTimeout() {
        wireMock.stubFor(post(urlEqualTo("/slow-post")).willReturn(aResponse().withFixedDelay(2_000)));

        try (AcmeHttpClient client = HttpClientFactory.create(wireMock.baseUrl(), Duration.ofMillis(500))) {
            assertThatThrownBy(() -> client.post("/slow-post", new Payment("42", 1.00), Account.class))
                    .isInstanceOf(UncheckedIOException.class)
                    .hasCauseInstanceOf(SocketTimeoutException.class);
        }

        wireMock.verify(1, postRequestedFor(urlEqualTo("/slow-post")));
    }

    private record Account(String id, double balance) {
    }

    private record Payment(String accountId, double amount) {
    }
}
