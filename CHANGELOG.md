# Changelog

All notable changes to this project are documented here.

## [1.4.0] - 2025-04

- Apply a connection-request timeout and configure bounded I/O retries.
- Update Jackson to 2.17.2.
- Document the exception and timeout contract.

## [1.3.0] - 2024-11

- Retry GET requests up to two times after HTTP 503 responses.

## [1.2.0] - 2024-08

- Add `AcmeHttpException.status()` and include a response-body snippet in error messages.

## [1.1.0] - 2024-06

- Add `create(baseUrl, Duration)` for configuring request timeouts.

## [1.0.0] - 2024-03

- Introduce the shared client factory with JSON GET and POST support.
