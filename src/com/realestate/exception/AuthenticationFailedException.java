package com.realestate.exception;

/**
 * Checked exception thrown when login credentials are invalid, the account is deactivated,
 * or the account is locked after 3 failed attempts.
 */
// [RUBRIC: OOP] Custom checked exception extending Exception
public class AuthenticationFailedException extends Exception {

    public AuthenticationFailedException(String message) {
        super(message);
    }

    public AuthenticationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
