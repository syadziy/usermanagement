package com.mac.usermanagement.utils.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid tenant, username, or password");
    }
}
