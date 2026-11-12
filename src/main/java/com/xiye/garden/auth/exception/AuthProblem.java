package com.xiye.garden.auth.exception;

import org.springframework.http.HttpStatus;

public class AuthProblem extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final long retryAfter;

    public AuthProblem(HttpStatus status, String code, String message) {
        this(status, code, message, 0);
    }

    public AuthProblem(HttpStatus status, String code, String message, long retryAfter) {
        super(message);
        this.status = status;
        this.code = code;
        this.retryAfter = retryAfter;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
    public long retryAfter() { return retryAfter; }
}
