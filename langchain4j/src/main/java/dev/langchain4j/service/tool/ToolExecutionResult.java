package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the result of a tool execution.
 *
 * @since 1.6.0
 */
public class ToolExecutionResult {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionResult.class);
    
    // Performance monitoring counters
    private static final AtomicLong creationCount = new AtomicLong(0);
    private static final AtomicLong accessCount = new AtomicLong(0);

    private final boolean isError;
    private final Object result;
    private final Supplier<String> resultTextSupplier;
    private final long creationTimestamp;

    public ToolExecutionResult(Builder builder) {
        this.isError = builder.isError;
        this.result = builder.result;
        this.resultTextSupplier = ensureNotNull(builder.resultTextSupplier, "resultTextSupplier");
        this.creationTimestamp = System.nanoTime();
        
        // Track creation for performance monitoring
        long currentCreationCount = creationCount.incrementAndGet();
        if (log.isDebugEnabled()) {
            log.debug("ToolExecutionResult created - Total creations: {}", currentCreationCount);
        }
        
        // Log creation vs access ratio periodically
        if (currentCreationCount % 100 == 0) {
            long currentAccessCount = accessCount.get();
            double accessRatio = currentAccessCount > 0 ? (double) currentAccessCount / currentCreationCount : 0.0;
            log.info("Lazy evaluation metrics - Creations: {}, Accesses: {}, Access ratio: {:.2f}", 
                    currentCreationCount, currentAccessCount, accessRatio);
        }
    }

    /**
     * Indicates whether the tool execution result represents an error.
     */
    public boolean isError() {
        return isError;
    }

    /**
     * Returns the tool execution result as object.
     * This object is the actual value returned by the tool.
     *
     * @see #resultText()
     */
    public Object result() {
        return result;
    }

    /**
     * Returns the tool execution result as text.
     * It is a {@link #result()} that is serialized into JSON string.
     *
     * @see #result()
     */
    public String resultText() {
        long startTime = System.nanoTime();
        
        try {
            String result = resultTextSupplier.get();
            
            // Track access for performance monitoring
            long currentAccessCount = accessCount.incrementAndGet();
            long executionTime = System.nanoTime() - startTime;
            long timeSinceCreation = startTime - creationTimestamp;
            
            if (log.isDebugEnabled()) {
                log.debug("ToolExecutionResult accessed - Total accesses: {}, Execution time: {} ns, Time since creation: {} ns", 
                        currentAccessCount, executionTime, timeSinceCreation);
            }
            
            // Log performance metrics for slow computations
            if (executionTime > 1_000_000) { // > 1ms
                log.warn("Slow lazy computation detected - Execution time: {} ns ({} ms)", 
                        executionTime, executionTime / 1_000_000);
            }
            
            return result;
        } catch (Exception e) {
            long executionTime = System.nanoTime() - startTime;
            log.error("Error during lazy computation - Execution time: {} ns", executionTime, e);
            throw e;
        }
    }

    /**
     * Indicates whether the tool execution result text has been computed.
     * Since caching is removed, this always returns true.
     *
     * @return true
     */
    public boolean isResultComputed() {
        return true;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ToolExecutionResult that = (ToolExecutionResult) object;
        return isError == that.isError
                && Objects.equals(result, that.result)
                && Objects.equals(resultText(), that.resultText());
    }

    @Override
    public int hashCode() {
        return Objects.hash(isError, result, resultText());
    }

    @Override
    public String toString() {
        return "ToolExecutionResult{" +
                "isError=" + isError +
                ", result=" + result +
                ", resultText='" + resultText() + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean isError;
        private Object result;
        private Supplier<String> resultTextSupplier;

        public Builder isError(boolean isError) {
            this.isError = isError;
            return this;
        }

        public Builder result(Object result) {
            this.result = result;
            return this;
        }

        public Builder resultText(String resultText) {
            this.resultTextSupplier = () -> resultText;
            return this;
        }

        public Builder resultTextSupplier(Supplier<String> resultTextSupplier) {
            this.resultTextSupplier = resultTextSupplier;
            return this;
        }

        public ToolExecutionResult build() {
            return new ToolExecutionResult(this);
        }
    }
}
