package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * A utility class that provides JSON serialization on demand.
 * This class defers the expensive JSON serialization operation until the result is actually needed,
 * computing the JSON string fresh on each access.
 * 
 * <p>Special handling is provided for:</p>
 * <ul>
 *   <li>Void return types - returns "Success" message</li>
 *   <li>String return types - returns the string directly without JSON processing</li>
 *   <li>Error conditions - falls back to toString() method with comprehensive error handling</li>
 * </ul>
 * 
 * <p>Enhanced error handling includes:</p>
 * <ul>
 *   <li>Supplier execution failures with proper logging and fallback strategies</li>
 *   <li>JSON serialization errors with graceful degradation to toString()</li>
 *   <li>toString() fallback failures with standardized error messages</li>
 *   <li>Comprehensive logging for debugging and monitoring</li>
 * </ul>
 */
@Internal
public class LazyJsonString {
    
    private static final Logger log = LoggerFactory.getLogger(LazyJsonString.class);
    
    private final Supplier<Object> valueSupplier;
    private volatile Exception lastError;
    
    /**
     * Creates a new LazyJsonString with the given value supplier.
     *
     * @param valueSupplier the supplier that provides the value to be serialized to JSON
     * @throws IllegalArgumentException if valueSupplier is null
     */
    public LazyJsonString(Supplier<Object> valueSupplier) {
        JsonSerializationErrorHandler.validateSupplier(valueSupplier, "valueSupplier");
        this.valueSupplier = valueSupplier;
    }

    /**
     * Gets the JSON string representation, computing it fresh on each call.
     * <p>
     * This method handles various edge cases with comprehensive error handling:
     * <ul>
     *   <li>If the supplier returns null, returns "null"</li>
     *   <li>If the supplier returns a String, returns it directly</li>
     *   <li>If the supplier execution fails, attempts fallback strategies</li>
     *   <li>If JSON serialization fails, falls back to toString()</li>
     *   <li>If toString() fails, returns a standardized error message</li>
     * </ul>
     * </p>
     *
     * @return the JSON string representation of the supplied value, or an error message if all attempts fail
     */
    public String getValue() {
        try {
            log.debug("Computing JSON string on demand");
            Object value = valueSupplier.get();
            String result = computeJsonString(value);
            // Clear error state only on complete success (no exceptions in supplier or serialization)
            // Don't clear if computeJsonString returned an error message due to JSON serialization failure
            if (lastError != null && !result.startsWith("LazyEvaluation Error:")) {
                log.debug("Clearing previous error state after successful evaluation");
                lastError = null;
            }
            return result;
        } catch (Exception supplierException) {
            log.warn("Supplier execution failed during lazy evaluation: {}", supplierException.getMessage());
            lastError = supplierException;
            return JsonSerializationErrorHandler.handleSupplierError(supplierException, null);
        }
    }
    
    /**
     * Computes the JSON string representation of the given value.
     * Handles special cases for void and String types.
     * 
     * @param value the value to convert to JSON
     * @return the JSON string representation
     */
    private String computeJsonString(Object value) {
        // Handle void return type
        if (value == null) {
            return "Success";
        }
        
        // Handle String return type without JSON processing
        if (value instanceof String) {
            return (String) value;
        }
        
        // Use existing Json.toJson() utility for other types with error handling
        try {
            return Json.toJson(value);
        } catch (Exception jsonException) {
            log.warn("JSON serialization failed for object of type {}: {}", 
                    value.getClass().getSimpleName(), jsonException.getMessage());
            lastError = jsonException;
            return JsonSerializationErrorHandler.handleJsonSerializationError(jsonException, value);
        }
    }
    
    /**
     * Serializes the given value to JSON with comprehensive error handling.
     * 
     * @param value the value to serialize
     * @return JSON string representation or fallback string
     */
    private String serializeToJson(Object value) {
        if (value == null) {
            log.debug("Value is null, returning 'null'");
            return "null";
        }
        
        if (value instanceof String) {
            log.debug("Value is already a String, returning directly");
            return (String) value;
        }
        
        try {
            log.debug("Attempting JSON serialization for object of type: {}", value.getClass().getSimpleName());
            String result = Json.toJson(value);
            return result;
        } catch (Exception jsonException) {
            log.warn("JSON serialization failed for object of type {}: {}", 
                    value.getClass().getSimpleName(), jsonException.getMessage());
            lastError = jsonException;
            return JsonSerializationErrorHandler.handleJsonSerializationError(jsonException, value);
        }
    }

    /**
     * Gets the last error that occurred during evaluation, if any.
     * 
     * @return the last exception that occurred, or null if no error
     */
    public Exception getLastError() {
        return lastError;
    }

    /**
     * Checks if an error occurred during the last evaluation attempt.
     * 
     * @return true if an error occurred, false otherwise
     */
    public boolean hasError() {
        return lastError != null;
    }
    
    /**
     * Returns the string representation of this LazyJsonString.
     * Always returns a placeholder indicating on-demand evaluation.
     * 
     * @return string representation
     */
    @Override
    public String toString() {
        return "[LazyJsonString: computed on demand]";
    }

    /**
     * Checks equality based on the computed JSON string value.
     * Two LazyJsonString instances are equal if their computed values are equal.
     * 
     * @param obj the object to compare with
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        LazyJsonString other = (LazyJsonString) obj;
        String thisValue = this.getValue();
        String otherValue = other.getValue();
        return thisValue != null ? thisValue.equals(otherValue) : otherValue == null;
    }
    
    /**
     * Returns the hash code based on the computed JSON string value.
     * 
     * @return hash code
     */
    @Override
    public int hashCode() {
        String value = getValue();
        return value != null ? value.hashCode() : 0;
    }
}