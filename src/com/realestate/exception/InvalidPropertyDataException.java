package com.realestate.exception;

/**
 * Checked exception thrown when property data fails validation or cannot be modified/deleted.
 */
// [RUBRIC: OOP] Custom checked exception extending Exception
public class InvalidPropertyDataException extends Exception {

    public InvalidPropertyDataException(String message) {
        super(message);
    }

    public InvalidPropertyDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
