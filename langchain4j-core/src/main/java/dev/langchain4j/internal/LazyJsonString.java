package dev.langchain4j.internal;

import dev.langchain4j.Internal;

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
 *   <li>Error conditions - falls back to toString() method</li>
 * </ul>
 */
@Internal
public class LazyJsonString {
    
    private final Supplier<Object> valueSupplier;
    
    /**
     * Creates a new LazyJsonString with the given value supplier.
     * 
     * @param valueSupplier the supplier that provides the value to be serialized to JSON
     * @throws IllegalArgumentException if valueSupplier is null
     */
    public LazyJsonString(Supplier<Object> valueSupplier) {
        if (valueSupplier == null) {
            throw new IllegalArgumentException("Value supplier cannot be null");
        }
        this.valueSupplier = valueSupplier;
    }
    
    /**
     * Gets the JSON string representation of the value, computing it fresh on each call.
     * 
     * @return the JSON string representation of the value
     */
    public String getValue() {
        try {
            Object value = valueSupplier.get();
            return computeJsonString(value);
        } catch (Exception e) {
            // Graceful error handling with fallback to toString()
            try {
                Object value = valueSupplier.get();
                return value != null ? value.toString() : "null";
            } catch (Exception fallbackException) {
                return "Error: " + e.getMessage();
            }
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
        
        // Use existing Json.toJson() utility for other types
        return Json.toJson(value);
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