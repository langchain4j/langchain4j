package dev.langchain4j.service.tool;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static dev.langchain4j.internal.Utils.copy;

/**
 * Represents the result of a tool execution.
 *
 * <p>The result can be provided in three mutually exclusive ways via the builder:
 * <ul>
 *   <li>{@link Builder#resultText(String)} — a text result, stored internally as a single {@link TextContent}.</li>
 *   <li>{@link Builder#resultTextSupplier(Supplier)} — a lazily computed text result.
 *       The supplier is invoked on first access to {@link #resultContents()} or {@link #resultText()}
 *       and the result is cached.</li>
 *   <li>{@link Builder#resultContents(List)} — a result containing any combination of
 *   {@link Content} elements (e.g. {@link TextContent}, {@link ImageContent}).</li>
 * </ul>
 *
 * <p>Regardless of which builder method was used, the canonical accessor is {@link #resultContents()},
 * which always returns a {@code List<Content>}. The convenience method {@link #resultText()} can be used
 * when the result is known to be a single {@link TextContent}; it throws {@link IllegalStateException} otherwise.
 *
 * @since 1.6.0
 */
public class ToolExecutionResult {

    private final boolean isError;
    private final Object result;
    private final AtomicReference<List<Content>> resultContents;
    private final Supplier<String> resultTextSupplier;
    private final Map<String, Object> attributes;

    public ToolExecutionResult(Builder builder) {
        this.isError = builder.isError;
        this.result = builder.result;

        boolean hasResultText = builder.resultText != null;
        boolean hasResultTextSupplier = builder.resultTextSupplier != null;
        boolean hasResultContents = builder.resultContents != null && !builder.resultContents.isEmpty();
        validate(hasResultText, hasResultTextSupplier, hasResultContents);
        if (hasResultText) {
            this.resultContents = new AtomicReference<>(List.of(TextContent.from(builder.resultText)));
            this.resultTextSupplier = null;
        } else if (hasResultTextSupplier) {
            this.resultContents = new AtomicReference<>();
            this.resultTextSupplier = builder.resultTextSupplier;
        } else {
            this.resultContents = new AtomicReference<>(copy(builder.resultContents));
            this.resultTextSupplier = null;
        }

        this.attributes = copy(builder.attributes);
    }

    private static void validate(boolean hasResultText, boolean hasResultTextSupplier, boolean hasResultContents) {
        int setCount = (hasResultText ? 1 : 0) + (hasResultTextSupplier ? 1 : 0) + (hasResultContents ? 1 : 0);
        if (setCount == 0) {
            throw new IllegalArgumentException(
                    "One of resultText, resultTextSupplier, or resultContents must be provided");
        }
        if (setCount > 1) {
            throw new IllegalArgumentException(
                    "resultText, resultTextSupplier, and resultContents are mutually exclusive");
        }
    }

    /**
     * Returns whether the tool execution resulted in an error.
     *
     * @return {@code true} if the tool execution failed, {@code false} otherwise.
     */
    public boolean isError() {
        return isError;
    }

    /**
     * Returns the raw object returned by the tool method.
     * This is the original value before any conversion to {@link Content}.
     * It is not sent to the LLM.
     *
     * @return the raw result object, or {@code null} if not set.
     * @see #resultText()
     * @see #resultContents()
     */
    public Object result() {
        return result;
    }

    /**
     * Returns the tool execution result as a plain text string.
     * This is a convenience method for when the result is known to be a single {@link TextContent}.
     *
     * @return the text of the single {@link TextContent} element.
     * @throws IllegalStateException if the result contains non-text or multiple content elements.
     *                               Use {@link #resultContents()} instead.
     * @see #resultContents()
     */
    public String resultText() {
        List<Content> contents = resultContents();
        if (contents.size() == 1 && contents.get(0) instanceof TextContent textContent) {
            return textContent.text();
        }
        throw new IllegalStateException(
                "resultText() cannot be called when resultContents contains non-text or multiple content elements. "
                        + "Use resultContents() instead.");
    }

    /**
     * Returns the contents of the tool execution result that will be sent to the LLM.
     *
     * <p>When built with {@link Builder#resultTextSupplier(Supplier)}, the contents are
     * calculated lazily on first access and then cached.
     *
     * <p>Thread-safety: in rare concurrent scenarios, the supplier may be invoked
     * multiple times, but only one result will be cached. Suppliers should be
     * idempotent and side-effect free.
     *
     * <p>Virtual thread friendly: uses lock-free atomic operations that do not
     * pin carrier threads.
     *
     * @return the list of {@link Content} elements, never {@code null}.
     * @since 1.13.0
     */
    @Experimental
    public List<Content> resultContents() {
        return resultContents.updateAndGet(current -> {
            if (current != null) {
                return current;
            }
            String text = resultTextSupplier.get();
            return text == null ? List.of() : List.of(TextContent.from(text));
        });
    }

    /**
     * Returns attributes associated with the tool execution.
     * Attributes are propagated into {@link ToolExecutionResultMessage#attributes()}
     * and can be persisted in a {@link ChatMemory}. They are not sent to the LLM.
     *
     * @return an unmodifiable map of attributes, or an empty map if none were set.
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
                && Objects.equals(resultContents(), that.resultContents())
                && Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isError, result, resultContents(), attributes);
    }

    @Override
    public String toString() {
        return "ToolExecutionResult {"
                + "isError = " + isError
                + ", result = " + result
                + ", resultContents = " + resultContents()
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
        private List<Content> resultContents;
        private Map<String, Object> attributes;

        /**
         * Sets whether the tool execution resulted in an error.
         *
         * @param isError {@code true} if the tool execution failed.
         * @return this builder.
         */
        public Builder isError(boolean isError) {
            this.isError = isError;
            return this;
        }

        /**
         * Sets the raw object returned by the tool method.
         * This value is not sent to the LLM.
         *
         * @param result the raw result object.
         * @return this builder.
         */
        public Builder result(Object result) {
            this.result = result;
            return this;
        }

        /**
         * Sets the result as a text string. The text will be wrapped into a single {@link TextContent}.
         * Mutually exclusive with {@link #resultTextSupplier(Supplier)} and {@link #resultContents(List)}.
         *
         * @param resultText the text result.
         * @return this builder.
         */
        public Builder resultText(String resultText) {
            this.resultText = resultText;
            return this;
        }

        /**
         * Sets a supplier for lazy calculation of the result text.
         * The supplier will be called on first access to {@link ToolExecutionResult#resultContents()}
         * and the result will be wrapped into a single {@link TextContent} and cached.
         * Mutually exclusive with {@link #resultText(String)} and {@link #resultContents(List)}.
         *
         * @param resultTextSupplier the supplier to calculate result text on demand.
         * @return this builder.
         * @since 1.9.0
         */
        public Builder resultTextSupplier(Supplier<String> resultTextSupplier) {
            this.resultTextSupplier = resultTextSupplier;
            return this;
        }

        /**
         * Sets the contents of the tool execution result.
         * Can contain any combination of {@link Content} elements
         * (e.g. {@link TextContent}, {@code ImageContent}).
         * Mutually exclusive with {@link #resultText(String)} and {@link #resultTextSupplier(Supplier)}.
         *
         * @param resultContents the contents.
         * @return this builder.
         * @since 1.13.0
         */
        @Experimental
        public Builder resultContents(List<Content> resultContents) {
            this.resultContents = resultContents;
            return this;
        }

        /**
         * Sets attributes associated with the tool execution.
         * Attributes are propagated into {@link ToolExecutionResultMessage#attributes()}
         * and can be persisted in a {@link ChatMemory}. They are not sent to the LLM.
         *
         * @param attributes the attributes.
         * @return this builder.
         * @since 1.12.0
         */
        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes;
            return this;
        }

        /**
         * Builds a {@link ToolExecutionResult}.
         *
         * @return the built instance.
         * @throws IllegalArgumentException if none of {@code resultText}, {@code resultTextSupplier},
         *                                  or {@code resultContents} was set, or if more than one was set.
         */
        public ToolExecutionResult build() {
            return new ToolExecutionResult(this);
        }
    }
}
