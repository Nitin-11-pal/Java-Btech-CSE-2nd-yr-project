package com.realestate.exception;

/**
 * Checked exception thrown when an actor attempts an operation without the required role or ownership.
 */
// [RUBRIC: OOP] Custom checked exception extending Exception
public class UnauthorizedActionException extends Exception {

    public UnauthorizedActionException(String message) {
        super(message);
    }

    public UnauthorizedActionException(String message, Throwable cause) {
        super(message, cause);
    }
}
