package dev.langchain4j.model.workersai.client;

import lombok.Data;

import java.util.List;

/**
 * Multiple models leverage the same output format, so we can use this class to parse the response.
 *
 * @param <T>
 *     Type of the result.
 */
@Data
public class ApiResponse<T> {

    /**
     * Result of the API call.
     */
    private T result;

    /**
     * Success of the API call.
     */
    private boolean success;

    /**
     * Errors of the API call.
     */
    private List<Error> errors;

    /**
     * Messages of the API call.
     */
    private List<String> messages;

    /**
     * Default constructor.
     */
    public ApiResponse() {}

    /**
     * Error class.
     */
    @Data
    public static class Error {
        /**
         * Message of the error.
         */
        private String message;
        /**
         * Code of the error.
         */
        private int code;
        /**
         * Default constructor.
         */
        public Error() {}
    }

}
