package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSource;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import java.util.Map;

/**
 * Represents the parameter passed to a tool input guardrail, before a tool is executed.
 *
 * @since 1.25.0
 */
@Experimental
public final class ToolInputGuardrailRequest implements GuardrailRequest<ToolInputGuardrailRequest> {

    private final ToolExecutionRequest toolExecutionRequest;
    private final ToolSpecification toolSpecification;
    private final ToolSource toolSource;
    private final GuardrailRequestParams requestParams;

    private ToolInputGuardrailRequest(Builder builder) {
        this.toolExecutionRequest = ensureNotNull(builder.toolExecutionRequest, "toolExecutionRequest");
        this.toolSpecification = builder.toolSpecification;
        this.toolSource = getOrDefault(builder.toolSource, ToolSource.programmatic());
        this.requestParams = ensureNotNull(builder.requestParams, "requestParams");
    }

    /**
     * @return the tool execution request, never null
     */
    public ToolExecutionRequest toolExecutionRequest() {
        return toolExecutionRequest;
    }

    /**
     * @return the specification of the tool being executed; may be null when it is not known
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
     * Parses the tool arguments JSON into a read-only map, using the same lenient parsing as the default executor
     * of local {@code @Tool} methods (null or blank arguments give an empty map; surrounding quotes, escaped quotes
     * and trailing commas are normalized when the strict parse fails).
     * <p>
     * This parity holds only for local {@code @Tool} methods. Other executors, such as MCP or programmatic ones,
     * may parse the raw arguments differently (for example strictly); {@link #arguments()} is the exact raw JSON
     * payload the executor receives.
     * <p>
     * These are the arguments of the tool call produced by the LLM and are <b>untrusted</b>; they are neither
     * the AI Service method arguments (see {@link InvocationContext#methodArguments()}) nor part of the
     * {@link ToolSource}.
     * <p>
     * The result is a deep, unmodifiable snapshot: nested objects are maps, nested arrays are lists, and JSON
     * {@code null} values are preserved as null map values. Number types depend on the JSON codec.
     * The map is parsed on every call.
     * <p>
     * If the arguments cannot be parsed, a {@link dev.langchain4j.exception.ToolArgumentsException} is thrown. A
     * guardrail can let it propagate: the tool is then not executed, and the call is handled like an arguments error of
     * the tool, by the {@code ToolArgumentsErrorHandler} of the AI service (for example, the error is sent to the LLM
     * so that it can correct the arguments), instead of aborting the invocation.
     *
     * @return the parsed arguments, never null
     * @throws dev.langchain4j.exception.ToolArgumentsException if the arguments are not a valid JSON object (malformed
     *         JSON, a non-object root, or the JSON literal {@code null})
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
     * Replaces the arguments JSON, keeping the id and the name of the tool execution request.
     *
     * @param text the new arguments JSON, must not be null
     * @throws IllegalArgumentException if text is null
     */
    @Override
    public ToolInputGuardrailRequest withText(String text) {
        ensureNotNull(text, "text");
        return toBuilder()
                .toolExecutionRequest(
                        toolExecutionRequest.toBuilder().arguments(text).build())
                .build();
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
     * Builder for {@link ToolInputGuardrailRequest}.
     *
     * @since 1.25.0
     */
    @Experimental
    public static class Builder {
        private ToolExecutionRequest toolExecutionRequest;
        private ToolSpecification toolSpecification;
        private ToolSource toolSource;
        private GuardrailRequestParams requestParams;

        private Builder() {}

        private Builder(ToolInputGuardrailRequest request) {
            this.toolExecutionRequest = request.toolExecutionRequest;
            this.toolSpecification = request.toolSpecification;
            this.toolSource = request.toolSource;
            this.requestParams = request.requestParams;
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
         * Builds the request.
         *
         * @return the request
         */
        public ToolInputGuardrailRequest build() {
            return new ToolInputGuardrailRequest(this);
        }
    }
}
