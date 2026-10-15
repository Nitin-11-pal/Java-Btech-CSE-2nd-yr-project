package com.realestate.exception;

/**
 * Checked exception thrown when a requested entity (User, Property, LeaseApplication) is not found.
 */
// [RUBRIC: OOP] Custom checked exception extending Exception
public class ResourceNotFoundException extends Exception {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
