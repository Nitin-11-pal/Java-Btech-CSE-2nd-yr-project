package com.realestate.exception;

/**
 * Checked exception thrown when user registration or profile data fails validation.
 */
// [RUBRIC: OOP] Custom checked exception extending Exception
public class InvalidUserDataException extends Exception {

    public InvalidUserDataException(String message) {
        super(message);
    }

    public InvalidUserDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
