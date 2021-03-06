package net.hh.request_dispatcher;

import java.io.Serializable;

/**
 * Wrapper class for Execptions thrown by RequestHandler
 */
public class RequestException extends RuntimeException implements Serializable {
    public RequestException() {
    }

    public RequestException(String message) {
        super(message);
    }

    public RequestException(String message, Throwable cause) {
        super(message, cause);
    }

    public RequestException(Throwable cause) {
        super(cause);
    }
}
