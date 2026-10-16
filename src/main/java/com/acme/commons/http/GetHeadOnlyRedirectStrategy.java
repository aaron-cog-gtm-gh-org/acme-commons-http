package com.acme.commons.http;

import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Follows redirects for GET and HEAD, and 303 See Other for any method, so that a redirected POST
 * is never silently replayed as a GET.
 */
final class GetHeadOnlyRedirectStrategy extends DefaultRedirectStrategy {
    @Override
    public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context)
            throws ProtocolException {
        String method = request.getMethod();
        boolean redirectable = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)
                || response.getCode() == HttpStatus.SC_SEE_OTHER;
        return redirectable && super.isRedirected(request, response, context);
    }
}
