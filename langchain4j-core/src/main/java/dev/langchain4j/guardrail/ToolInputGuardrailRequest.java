package dev.langchain4j.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSource;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.internal.ToolArgumentsParser;
import dev.langchain4j.invocation.InvocationContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the parameter passed to a tool input guardrail, before a tool is executed.
 *
 * @since 1.24.0
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
        this.toolSource = builder.toolSource != null ? builder.toolSource : ToolSource.programmatic();
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
     *
     * @return the parsed arguments, never null
     * @throws IllegalArgumentException if the arguments are not a valid JSON object (malformed JSON, a non-object
     *                                  root, or the JSON literal {@code null})
     * @since 1.24.0
     */
    public Map<String, Object> argumentsAsMap() {
        try {
            Map<String, Object> parsed = ToolArgumentsParser.argumentsAsMap(arguments());
            // the JSON literal `null` parses to a null map
            return freezeMap(ensureNotNull(parsed, "parsed arguments"));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Tool arguments are not a valid JSON object: " + e.getMessage(), e);
        }
    }

    private static Map<String, Object> freezeMap(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(String.valueOf(key), freeze(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object freeze(Object value) {
        if (value instanceof Map<?, ?> map) {
            return freezeMap(map);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(element -> copy.add(freeze(element)));
            return Collections.unmodifiableList(copy);
        }
        return value;
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
     * @since 1.24.0
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

        public Builder toolExecutionRequest(ToolExecutionRequest toolExecutionRequest) {
            this.toolExecutionRequest = toolExecutionRequest;
            return this;
        }

        public Builder toolSpecification(ToolSpecification toolSpecification) {
            this.toolSpecification = toolSpecification;
            return this;
        }

        public Builder toolSource(ToolSource toolSource) {
            this.toolSource = toolSource;
            return this;
        }

        public Builder requestParams(GuardrailRequestParams requestParams) {
            this.requestParams = requestParams;
            return this;
        }

        public ToolInputGuardrailRequest build() {
            return new ToolInputGuardrailRequest(this);
        }
    }
}
