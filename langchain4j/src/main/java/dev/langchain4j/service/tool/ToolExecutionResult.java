package dev.langchain4j.service.tool;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.quoted;

/**
 * Represents the result of a tool execution.
 *
 * @since 1.6.0
 */
public class ToolExecutionResult {

    private final boolean isError;
    private final Object result;
    private final AtomicReference<String> resultText;
    private final Supplier<String> resultTextSupplier;
    private final Map<String, Object> attributes;

    public ToolExecutionResult(Builder builder) {
        this.isError = builder.isError;
        this.result = builder.result;

        // If resultText is provided directly, use it; otherwise use the supplier
        if (builder.resultText != null) {
            this.resultText = new AtomicReference<>(builder.resultText);
            this.resultTextSupplier = null;
        } else if (builder.resultTextSupplier != null) {
            this.resultText = new AtomicReference<>();
            this.resultTextSupplier = builder.resultTextSupplier;
        } else {
            throw new IllegalArgumentException("Either resultText or resultTextSupplier must be provided");
        }

        this.attributes = copy(builder.attributes);
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
     * The text is calculated lazily on first access and then cached.
     *
     * <p>Thread-safety: In rare concurrent scenarios, the supplier may be invoked
     * multiple times, but only one result will be cached. Suppliers should be
     * idempotent and side-effect free.
     *
     * <p>Virtual thread friendly: Uses lock-free atomic operations that do not
     * pin carrier threads.
     *
     * @see #result()
     */
    public String resultText() {
        return resultText.updateAndGet(
                current -> current != null ? current : (resultTextSupplier != null ? resultTextSupplier.get() : null));
    }

    /**
     * Returns attributes associated with tool execution.
     * These attributes will be propagated into {@link ToolExecutionResultMessage#attributes()}
     * and can be persisted in a {@link ChatMemory}. They will not be sent to the LLM.
     *
     * @since 1.12.0
     */
    public Map<String, Object> attributes() {
        return attributes;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ToolExecutionResult that = (ToolExecutionResult) object;
        return isError == that.isError
                && Objects.equals(result, that.result)
                && Objects.equals(resultText(), that.resultText())
                && Objects.equals(attributes, attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isError, result, resultText(), attributes);
    }

    @Override
    public String toString() {
        return "ToolExecutionResult {"
                + "isError = " + isError
                + ", result = " + result
                + ", resultText = " + quoted(resultText())
                + ", attributes = " + attributes
                + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean isError;
        private Object result;
        private String resultText;
        private Supplier<String> resultTextSupplier;
        private Map<String, Object> attributes;

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
            this.resultTextSupplier = null;
            return this;
        }

        /**
         * Sets a supplier for lazy calculation of the result text.
         * The supplier will be called only when {@link ToolExecutionResult#resultText()} is first accessed.
         *
         * @param resultTextSupplier the supplier to calculate result text on demand
         * @return this builder
         * @since 1.9.0
         */
        public Builder resultTextSupplier(Supplier<String> resultTextSupplier) {
            this.resultTextSupplier = resultTextSupplier;
            this.resultText = null;
            return this;
        }

        /**
         * Sets attributes associated with tool execution.
         * These attributes will be propagated into {@link ToolExecutionResultMessage#attributes()}
         * and can be persisted in a {@link ChatMemory}. They will not be sent to the LLM.
         *
         * @since 1.12.0
         */
        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        public ToolExecutionResult build() {
            return new ToolExecutionResult(this);
        }
    }
}
