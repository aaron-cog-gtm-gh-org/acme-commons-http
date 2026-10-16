# Acme Commons HTTP

Acme Commons HTTP is the shared JSON-over-HTTP client used by Acme Bank services. It provides consistent request timeouts, response mapping, status handling, and bounded retries for service-to-service calls.

## Add the dependency

```xml
<dependency>
  <groupId>com.acme.commons</groupId>
  <artifactId>acme-commons-http</artifactId>
  <version>1.5.0</version>
</dependency>
```

## Usage

```java
try (AcmeHttpClient client = HttpClientFactory.create("https://accounts.acme.example")) {
    Account account = client.get("/v1/accounts/42", Account.class);
    PaymentReceipt receipt = client.post(
        "/v1/payments", new PaymentRequest("42", 25.00), PaymentReceipt.class);
}
```

`create(baseUrl)` uses a five-second timeout. `create(baseUrl, Duration)` applies the supplied timeout to connection acquisition, connection establishment, and socket reads. Successful JSON responses are mapped to the requested type; empty responses and `Void.class` produce `null`. `String.class` returns the response body as-is.

## Behaviour and error handling

| Situation | Result |
| --- | --- |
| Successful JSON GET or POST | Response is deserialized; GET and POST send `Accept: application/json`, and POST sends a JSON body. |
| Empty successful response or `Void.class` | Returns `null`. |
| Non-2xx status, including 404, 500, or an exhausted 503 | Throws `AcmeHttpException`; `status()` returns the HTTP status and the message includes up to 200 response-body characters. |
| GET response status 503 | Retries at most twice, 100 ms apart; a succeeding attempt returns normally. |
| POST response status 503 | Not retried. |
| Connection refused | Throws `UncheckedIOException` wrapping `org.apache.hc.client5.http.HttpHostConnectException` (a `java.net.ConnectException` subtype). Match on `ConnectException` rather than the Apache type. |
| Read timeout | Throws `UncheckedIOException` with a `SocketTimeoutException` cause. |
| Redirect | GET and HEAD follow redirects; other methods follow only 303. A redirected POST is returned as an `AcmeHttpException`, never replayed as a GET. |
| Closed client | `get` and `post` throw `IllegalStateException`; `close()` is idempotent. |

The transport is Apache HttpClient 5 (`org.apache.httpcomponents.client5:httpclient5`). It retries I/O failures of idempotent requests up to two times; connection refusals, timeouts, and unknown hosts are not retried. Each client uses a connection pool of 20 connections, 2 per route, and revalidates connections idle for more than two seconds. The service-unavailable policy is limited to GET and status 503.

## Build

Dependencies resolve through Acme Artifactory (`maven-virtual`); `.mvn/settings.xml` mirrors every repository to `ARTIFACTORY_URL`.

```sh
ARTIFACTORY_URL=http://localhost:8081/artifactory/maven-virtual \
  mvn -B -s .mvn/settings.xml verify
```

## Releasing

Bump the release version on `main`. The `publish` workflow deploys the Maven artifacts to the `maven-repo` branch. Consumers resolve releases through Acme Artifactory's `maven-virtual` repository.
