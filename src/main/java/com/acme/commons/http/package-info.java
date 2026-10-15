/**
 * Shared HTTP client utilities for Acme service integrations.
 *
 * <p>Example:</p>
 * <pre>{@code
 * try (AcmeHttpClient client = HttpClientFactory.create("https://accounts.acme.example")) {
 *     Account account = client.get("/v1/accounts/42", Account.class);
 * }
 * }</pre>
 */
package com.acme.commons.http;
