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
| Connection refused | Throws `UncheckedIOException` with a `java.net.ConnectException` cause. Match on `ConnectException`, not on transport-specific subtypes. |
| Read timeout | Throws `UncheckedIOException` with a `SocketTimeoutException` cause. |
| Closed client | `get` and `post` throw `IllegalStateException`; `close()` is idempotent. |

Idempotent requests are retried up to two times after recoverable I/O failures; POST is never retried. GET requests are retried up to two times after HTTP 503, 100 ms apart (`Retry-After` is ignored). Other statuses, including 429, are not retried.

GET and HEAD follow 301, 302, 307 and 308 redirects. A 303 response is followed with a GET. POST requests that receive 301, 302, 307 or 308 are not followed and throw `AcmeHttpException` with that status.

Each client pools up to 2 connections per route and 20 in total. Pooled connections idle for more than two seconds are validated before reuse. Requests carry `User-Agent: acme-commons-http/<version>`.

## Build

Dependencies resolve through Acme Artifactory (`maven-virtual`); `.mvn/settings.xml` mirrors every repository to `ARTIFACTORY_URL`.

```sh
ARTIFACTORY_URL=http://localhost:8081/artifactory/maven-virtual \
  mvn -B -s .mvn/settings.xml verify
```

## Releasing

Bump the release version on `main`. The `publish` workflow deploys the Maven artifacts to the `maven-repo` branch. Consumers resolve releases through Acme Artifactory's `maven-virtual` repository.
