package com.mohitdeo.booking.api;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Base type for business errors. Each subclass carries the HTTP status and a short title,
 * so a single exception handler can render every failure as an RFC 9457 problem detail.
 */
public abstract class BookingException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    protected BookingException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** Extra machine-readable fields added to the problem detail body. */
    public Map<String, Object> properties() {
        return Map.of();
    }
}
