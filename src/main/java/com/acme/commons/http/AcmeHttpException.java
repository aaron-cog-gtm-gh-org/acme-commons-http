package com.acme.commons.http;

/**
 * Indicates that an Acme service returned a non-success HTTP response.
 */
public class AcmeHttpException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final int status;

    /**
     * Creates an exception for a response status and diagnostic message.
     *
     * @param status HTTP response status code
     * @param message response diagnostic
     */
    public AcmeHttpException(int status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Returns the HTTP response status code.
     *
     * @return response status
     */
    public int status() {
        return status;
    }
}
