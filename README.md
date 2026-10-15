# Acme Commons HTTP

Acme Commons HTTP is the shared JSON-over-HTTP client used by Acme Bank services. It provides consistent request timeouts, response mapping, status handling, and bounded retries for service-to-service calls.

## Add the dependency

```xml
<dependency>
  <groupId>com.acme.commons</groupId>
  <artifactId>acme-commons-http</artifactId>
  <version>1.4.0</version>
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
| Connection refused | Throws `UncheckedIOException` wrapping `org.apache.http.conn.HttpHostConnectException` (a `ConnectException` subtype). |
| Read timeout | Throws `UncheckedIOException` with a `SocketTimeoutException` cause. |
| Closed client | `get` and `post` throw `IllegalStateException`; `close()` is idempotent. |

The Apache transport can retry I/O failures up to two times for requests it considers safe to retry. The service-unavailable policy is limited to GET and status 503.

## Build

```sh
ARTIFACTORY_URL=https://maven-central.storage-download.googleapis.com/maven2 \
  mvn -B -s .mvn/settings.xml verify
```

## Releasing

Bump the release version on `main`. The `publish` workflow deploys the Maven artifacts to the `maven-repo` branch. Consumers resolve releases through Acme Artifactory's `maven-virtual` repository.
