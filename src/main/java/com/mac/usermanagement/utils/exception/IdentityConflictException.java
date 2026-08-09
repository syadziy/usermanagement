package com.mac.usermanagement.utils.exception;

public class IdentityConflictException extends RuntimeException {

    public IdentityConflictException(String message) {
        super(message);
    }

    public IdentityConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
