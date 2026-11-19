package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSource;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.invocation.InvocationContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Represents the parameter passed to a tool output guardrail, after a tool has been executed.
 *
 * @since 1.25.0
 */
@Experimental
public final class ToolOutputGuardrailRequest implements GuardrailRequest<ToolOutputGuardrailRequest> {

    private final ToolExecutionRequest toolExecutionRequest;
    private final ToolSpecification toolSpecification;
    private final ToolSource toolSource;
    private final GuardrailRequestParams requestParams;
    private final List<Content> resultContents;
    private final boolean isError;

    private ToolOutputGuardrailRequest(Builder builder) {
        this.toolExecutionRequest = ensureNotNull(builder.toolExecutionRequest, "toolExecutionRequest");
        this.toolSpecification = builder.toolSpecification;
        this.toolSource = builder.toolSource != null ? builder.toolSource : ToolSource.programmatic();
        this.requestParams = ensureNotNull(builder.requestParams, "requestParams");

        this.resultContents = List.copyOf(Optional.ofNullable(builder.resultContents)
                .map(List::stream)
                .orElseGet(Stream::empty)
                .map(contents -> ensureNotNull(contents, "resultContents element"))
                .toList());

        this.isError = builder.isError;
    }

    /**
     * @return the tool execution request, never null
     */
    public ToolExecutionRequest toolExecutionRequest() {
        return toolExecutionRequest;
    }

    /**
     * @return the specification of the executed tool; may be null when it is not known
     */
    public ToolSpecification toolSpecification() {
        return toolSpecification;
    }

    /**
     * @return the trusted origin of the tool, never null ({@link ToolSource#programmatic()} by default)
     */
    public ToolSource toolSource() {
        return toolSource;
    }

    @Override
    public GuardrailRequestParams requestParams() {
        return requestParams;
    }

    /**
     * @return the tool name
     */
    public String toolName() {
        return toolExecutionRequest.name();
    }

    /**
     * @return the tool arguments as a JSON string
     */
    public String arguments() {
        return toolExecutionRequest.arguments();
    }

    /**
     * Parses the arguments the tool was executed with (after the rewrites of the input guardrails) into a read-only
     * map, exactly like {@link ToolInputGuardrailRequest#argumentsAsMap()}: leniently, as the default executor of local
     * {@code @Tool} methods does, into a deep, unmodifiable snapshot, parsed on every call.
     * <p>
     * These are the arguments of the tool call produced by the LLM, and are <b>untrusted</b>.
     *
     * @return the parsed arguments, never null
     * @throws dev.langchain4j.exception.ToolArgumentsException if the arguments are not a valid JSON object. Thrown
     *         from an output guardrail, it aborts the invocation like any other exception of a guardrail.
     * @since 1.25.0
     */
    public Map<String, Object> argumentsAsMap() {
        return ToolGuardrailArguments.asMap(arguments());
    }

    /**
     * @return the invocation context, or null if the request params carry none
     */
    public InvocationContext invocationContext() {
        return requestParams.invocationContext();
    }

    /**
     * @return the chat memory id, or null if there is no invocation context or no memory id
     */
    public Object memoryId() {
        InvocationContext context = invocationContext();
        return context == null ? null : context.chatMemoryId();
    }

    /**
     * @return all result contents (immutable, never null, possibly empty)
     */
    public List<Content> resultContents() {
        return resultContents;
    }

    /**
     * @return whether the tool execution resulted in an error
     */
    public boolean isError() {
        return isError;
    }

    /**
     * Returns the texts of all {@link TextContent}s joined with {@code \n}. Never throws.
     *
     * @return the joined text, or an empty string if there is no text content
     */
    public String text() {
        return resultContents.stream()
                .filter(TextContent.class::isInstance)
                .map(c -> ((TextContent) c).text())
                .collect(Collectors.joining("\n"));
    }

    /**
     * Replaces the text contents with a single {@link TextContent} (placed where the first text content was, or first
     * if there was none), keeping the non-text contents and the error flag.
     *
     * @param text the new text, must not be null
     * @throws IllegalArgumentException if text is null
     */
    @Override
    public ToolOutputGuardrailRequest withText(String text) {
        ensureNotNull(text, "text");
        return withContents(replaceText(this.resultContents, text), isError);
    }

    /**
     * Replaces the text contents with a single {@link TextContent}, placed where the first text content was (or
     * first if there was none), keeping the non-text contents.
     */
    static List<Content> replaceText(List<Content> contents, String text) {
        var rewritten = new ArrayList<Content>();
        var textAdded = false;

        for (var content : contents) {
            if (content instanceof TextContent) {
                if (!textAdded) {
                    rewritten.add(new TextContent(text));
                    textAdded = true;
                }
            } else {
                rewritten.add(content);
            }
        }

        if (!textAdded) {
            rewritten.add(0, new TextContent(text));
        }

        return rewritten;
    }

    ToolOutputGuardrailRequest withContents(List<Content> contents, boolean isError) {
        return toBuilder().resultContents(contents).isError(isError).build();
    }

    /**
     * @return a builder initialized with the values of this request
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link ToolOutputGuardrailRequest}.
     *
     * @since 1.25.0
     */
    @Experimental
    public static class Builder {
        private ToolExecutionRequest toolExecutionRequest;
        private ToolSpecification toolSpecification;
        private ToolSource toolSource;
        private GuardrailRequestParams requestParams;
        private List<Content> resultContents;
        private boolean isError;

        private Builder() {}

        private Builder(ToolOutputGuardrailRequest request) {
            this.toolExecutionRequest = request.toolExecutionRequest;
            this.toolSpecification = request.toolSpecification;
            this.toolSource = request.toolSource;
            this.requestParams = request.requestParams;
            this.resultContents = request.resultContents;
            this.isError = request.isError;
        }

        /**
         * Sets the tool execution request, required.
         *
         * @param toolExecutionRequest the tool execution request
         * @return this builder
         */
        public Builder toolExecutionRequest(ToolExecutionRequest toolExecutionRequest) {
            this.toolExecutionRequest = toolExecutionRequest;
            return this;
        }

        /**
         * Sets the specification of the tool, if known.
         *
         * @param toolSpecification the tool specification
         * @return this builder
         */
        public Builder toolSpecification(ToolSpecification toolSpecification) {
            this.toolSpecification = toolSpecification;
            return this;
        }

        /**
         * Sets where the tool comes from. Defaults to {@link ToolSource#programmatic()}.
         *
         * @param toolSource the tool source
         * @return this builder
         */
        public Builder toolSource(ToolSource toolSource) {
            this.toolSource = toolSource;
            return this;
        }

        /**
         * Sets the parameters of the AI service invocation, required.
         *
         * @param requestParams the request params
         * @return this builder
         */
        public Builder requestParams(GuardrailRequestParams requestParams) {
            this.requestParams = requestParams;
            return this;
        }

        /**
         * Sets the contents of the tool result.
         *
         * @param resultContents the contents
         * @return this builder
         */
        public Builder resultContents(List<Content> resultContents) {
            this.resultContents = resultContents;
            return this;
        }

        /**
         * Sets whether the tool result is an error.
         *
         * @param isError whether the result is an error
         * @return this builder
         */
        public Builder isError(boolean isError) {
            this.isError = isError;
            return this;
        }

        /**
         * Builds the request.
         *
         * @return the request
         */
        public ToolOutputGuardrailRequest build() {
            return new ToolOutputGuardrailRequest(this);
        }
    }
}
