package com.acme.commons.http;

import org.apache.hc.client5.http.impl.DefaultRedirectStrategy;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.ProtocolException;
import org.apache.hc.core5.http.protocol.HttpContext;

/**
 * Follows 301, 302, 307 and 308 redirects for GET and HEAD only, and 303 for any method (re-issued as GET).
 * A POST that receives 301, 302, 307 or 308 is not followed, so its body is never replayed or turned into a GET.
 */
final class SafeMethodRedirectStrategy extends DefaultRedirectStrategy {
    @Override
    public boolean isRedirected(HttpRequest request, HttpResponse response, HttpContext context)
            throws ProtocolException {
        if (!super.isRedirected(request, response, context)) {
            return false;
        }
        if (response.getCode() == HttpStatus.SC_SEE_OTHER) {
            return true;
        }
        return Method.GET.isSame(request.getMethod()) || Method.HEAD.isSame(request.getMethod());
    }
}
