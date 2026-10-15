package com.realestate.util;

/**
 * Generic value wrapper representing either a successful operation result or an error message.
 *
 * @param <T> the payload type carried on success
 */
// [RUBRIC: Collections] Generic Result<T> utility class
public class Result<T> {

    private final boolean success;
    private final T data;
    private final String errorMessage;

    private Result(boolean success, T data, String errorMessage) {
        this.success = success;
        this.data = data;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a successful Result wrapping the given data payload.
     *
     * @param data the result value
     * @param <T>  type of the result value
     * @return successful Result instance
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(true, data, null);
    }

    /**
     * Creates a failed Result with an explanatory error message.
     *
     * @param errorMessage reason for failure
     * @param <T>          expected payload type
     * @return failed Result instance
     */
    public static <T> Result<T> fail(String errorMessage) {
        return new Result<>(false, null, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
