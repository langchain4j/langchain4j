package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.exception.LangChain4jException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized error handling utility for JSON serialization failures in lazy evaluation.
 * <p>
 * This utility provides consistent error handling patterns, standardized error messages,
 * and proper logging for JSON serialization errors that occur during lazy evaluation.
 * </p>
 */
@Internal
public final class JsonSerializationErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(JsonSerializationErrorHandler.class);

    // Standardized error messages
    private static final String SUPPLIER_EXECUTION_ERROR = "Failed to execute value supplier during lazy evaluation";
    private static final String JSON_SERIALIZATION_ERROR = "Failed to serialize object to JSON during lazy evaluation";
    private static final String FALLBACK_SERIALIZATION_ERROR = "Failed to serialize object using toString() fallback";
    private static final String MULTIPLE_FAILURES_ERROR = "Multiple failures occurred during lazy evaluation";

    private JsonSerializationErrorHandler() {
        // Utility class - prevent instantiation
    }

    /**
     * Handles supplier execution errors with proper logging and fallback strategies.
     *
     * @param supplierException the exception that occurred during supplier execution
     * @param fallbackSupplier a fallback supplier to attempt if the primary fails
     * @return a fallback result or error message
     */
    public static String handleSupplierError(
            Exception supplierException, java.util.function.Supplier<Object> fallbackSupplier) {
        log.warn(SUPPLIER_EXECUTION_ERROR + ": {}", supplierException.getMessage(), supplierException);

        if (fallbackSupplier != null) {
            try {
                Object fallbackValue = fallbackSupplier.get();
                return handleToStringFallback(fallbackValue);
            } catch (Exception fallbackException) {
                log.error(
                        MULTIPLE_FAILURES_ERROR + " - Original: {}, Fallback: {}",
                        supplierException.getMessage(),
                        fallbackException.getMessage(),
                        fallbackException);
                return createErrorMessage(supplierException, fallbackException);
            }
        }

        return createErrorMessage(supplierException);
    }

    /**
     * Handles JSON serialization errors with proper logging and fallback to toString().
     *
     * @param serializationException the JSON serialization exception
     * @param originalValue the value that failed to serialize
     * @return a fallback string representation or error message
     */
    public static String handleJsonSerializationError(Exception serializationException, Object originalValue) {
        log.warn(
                JSON_SERIALIZATION_ERROR + " for object of type {}: {}",
                originalValue != null ? originalValue.getClass().getSimpleName() : "null",
                serializationException.getMessage(),
                serializationException);

        return handleToStringFallback(originalValue);
    }

    /**
     * Handles toString() fallback with error handling.
     *
     * @param value the value to convert using toString()
     * @return string representation or error message
     */
    public static String handleToStringFallback(Object value) {
        if (value == null) {
            return "null";
        }

        try {
            String result = value.toString();
            log.debug(
                    "Successfully used toString() fallback for object of type {}",
                    value.getClass().getSimpleName());
            return result;
        } catch (Exception toStringException) {
            log.error(
                    FALLBACK_SERIALIZATION_ERROR + " for object of type {}: {}",
                    value.getClass().getSimpleName(),
                    toStringException.getMessage(),
                    toStringException);
            return createErrorMessage(toStringException);
        }
    }

    /**
     * Creates a standardized error message for single failures.
     *
     * @param exception the exception that occurred
     * @return standardized error message
     */
    public static String createErrorMessage(Exception exception) {
        return String.format(
                "LazyEvaluation Error: %s - %s",
                exception.getClass().getSimpleName(),
                exception.getMessage() != null ? exception.getMessage() : "Unknown error");
    }

    /**
     * Creates a standardized error message for multiple failures.
     *
     * @param primaryException the primary exception
     * @param fallbackException the fallback exception
     * @return standardized error message for multiple failures
     */
    public static String createErrorMessage(Exception primaryException, Exception fallbackException) {
        return String.format(
                "LazyEvaluation Multiple Errors: Primary[%s: %s], Fallback[%s: %s]",
                primaryException.getClass().getSimpleName(),
                primaryException.getMessage() != null ? primaryException.getMessage() : "Unknown error",
                fallbackException.getClass().getSimpleName(),
                fallbackException.getMessage() != null ? fallbackException.getMessage() : "Unknown error");
    }

    /**
     * Creates a LangChain4jException for critical errors that should propagate.
     *
     * @param message the error message
     * @param cause the underlying cause
     * @return a LangChain4jException
     */
    public static LangChain4jException createCriticalError(String message, Throwable cause) {
        log.error("Critical error in lazy evaluation: {}", message, cause);
        return new LangChain4jException(message, cause);
    }

    /**
     * Validates that a supplier is not null and throws appropriate exception if it is.
     *
     * @param supplier the supplier to validate
     * @param parameterName the name of the parameter for error messages
     * @throws IllegalArgumentException if supplier is null
     */
    public static void validateSupplier(java.util.function.Supplier<?> supplier, String parameterName) {
        if (supplier == null) {
            String message = String.format("Supplier parameter '%s' cannot be null", parameterName);
            log.error(message);
            throw new IllegalArgumentException(message);
        }
    }
}
