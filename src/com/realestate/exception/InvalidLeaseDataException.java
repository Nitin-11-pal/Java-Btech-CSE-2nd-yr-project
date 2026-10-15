package com.realestate.exception;

/**
 * Checked exception thrown when lease application data or state transition is invalid.
 */
// [RUBRIC: OOP] Custom checked exception extending Exception
public class InvalidLeaseDataException extends Exception {

    public InvalidLeaseDataException(String message) {
        super(message);
    }

    public InvalidLeaseDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
