package dev.langchain4j.service.tool;

import dev.langchain4j.internal.Json;

import java.util.Objects;
import java.util.function.Supplier;

import static dev.langchain4j.internal.Utils.quoted;

/**
 * Represents the result of a tool execution.
 */
public class ToolExecutionResult {

    private final boolean isError;
    private final Object result;
    private final String resultText;
    private final Supplier<Object> lazyResultText;
    private final boolean useLazyEvaluation;
    private volatile String cachedLazyResult;
    private volatile boolean isLazyResultComputed = false;
    private final Object lazyLock = new Object();

    public ToolExecutionResult(boolean isError, Object result, String resultText) {
        this.isError = isError;
        this.result = result;
        this.resultText = resultText;
        this.lazyResultText = null;
        this.useLazyEvaluation = false;
    }
    
    // New constructor for lazy evaluation
    public ToolExecutionResult(boolean isError, Object result, String resultText, 
                             Supplier<Object> lazyResultText, boolean useLazyEvaluation) {
        this.isError = isError;
        this.result = result;
        this.resultText = resultText;
        this.lazyResultText = lazyResultText;
        this.useLazyEvaluation = useLazyEvaluation;
    }

    public boolean isError() {
        return isError;
    }

    public Object result() {
        return result;
    }

    public String resultText() {
        if (useLazyEvaluation && lazyResultText != null) {
            return getLazyResultText();
        }
        return resultText;
    }
    
    /**
     * Checks if the result text has been computed when using lazy evaluation.
     * 
     * @return true if lazy result text has been computed, false otherwise.
     *         Always returns true when not using lazy evaluation.
     */
    public boolean isResultTextComputed() {
        if (!useLazyEvaluation) {
            return true;
        }
        return isLazyResultComputed;
    }
    
    private String getLazyResultText() {
        if (!isLazyResultComputed) {
            synchronized (lazyLock) {
                if (!isLazyResultComputed) {
                    try {
                        Object value = lazyResultText.get();
                        cachedLazyResult = computeJsonString(value);
                    } catch (Exception e) {
                        cachedLazyResult = handleSerializationError(e);
                    } finally {
                        isLazyResultComputed = true;
                    }
                }
            }
        }
        return cachedLazyResult;
    }
    
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
    
    private String handleSerializationError(Exception e) {
        // Attempt fallback to toString()
        if (lazyResultText != null) {
            try {
                Object value = lazyResultText.get();
                return value != null ? value.toString() : "null";
            } catch (Exception fallbackException) {
                // Final fallback
                return "Error: " + e.getMessage();
            }
        }
        return "Error: " + e.getMessage();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ToolExecutionResult that = (ToolExecutionResult) o;
        return isError == that.isError &&
                Objects.equals(result, that.result) &&
                Objects.equals(resultText(), that.resultText());
    }

    @Override
    public int hashCode() {
        return Objects.hash(isError, result, resultText());
    }

    @Override
    public String toString() {
        return "ToolExecutionResult {" +
                " isError = " + isError +
                ", result = " + quoted(result) +
                ", resultText = " + quoted(resultText()) +
                " }";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean isError;
        private Object result;
        private String resultText;
        private Supplier<Object> lazyResultText;
        private boolean useLazyEvaluation = false;

        public Builder isError(boolean isError) {
            this.isError = isError;
            return this;
        }

        public Builder result(Object result) {
            this.result = result;
            return this;
        }

        public Builder resultText(String resultText) {
            this.resultText = resultText;
            return this;
        }
        
        /**
         * Enables lazy evaluation with the provided result text supplier.
         * When lazy evaluation is enabled, the result text will be computed on-demand
         * using the supplier and cached for subsequent access.
         * 
         * @param lazyResultText the supplier that provides the result text value
         * @return this builder
         */
        public Builder lazyResultText(Supplier<Object> lazyResultText) {
            this.lazyResultText = lazyResultText;
            this.useLazyEvaluation = true;
            return this;
        }
        
        /**
         * Explicitly controls whether to use lazy evaluation.
         * 
         * @param useLazyEvaluation true to enable lazy evaluation, false to disable
         * @return this builder
         */
        public Builder useLazyEvaluation(boolean useLazyEvaluation) {
            this.useLazyEvaluation = useLazyEvaluation;
            return this;
        }

        public ToolExecutionResult build() {
            return new ToolExecutionResult(isError, result, resultText, lazyResultText, useLazyEvaluation);
        }
    }
}
