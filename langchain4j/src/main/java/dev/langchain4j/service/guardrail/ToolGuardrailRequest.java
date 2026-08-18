package dev.langchain4j.service.guardrail;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionRequestUtil;
import java.util.Map;

/**
 * Common contract of the requests passed to a {@link ToolGuardrail}.
 *
 * @param <P> the concrete request type
 * @since 1.19.0
 */
@Experimental
public sealed interface ToolGuardrailRequest<P extends ToolGuardrailRequest<P>>
        permits ToolInputGuardrailRequest, ToolOutputGuardrailRequest {

    /**
     * The tool call requested by the LLM.
     */
    ToolExecutionRequest executionRequest();

    /**
     * The specification of the tool being called, and the object declaring it when there is one.
     */
    ToolMetadata toolMetadata();

    /**
     * The context of the AI Service invocation that triggered this tool call.
     */
    InvocationContext invocationContext();

    /**
     * The name of the tool being called.
     */
    default String toolName() {
        return executionRequest().name();
    }

    /**
     * The raw JSON arguments of the tool call.
     */
    default String arguments() {
        return executionRequest().arguments();
    }

    /**
     * The arguments of the tool call, parsed into a map. Returns an empty map when the arguments are
     * absent or cannot be parsed.
     */
    default Map<String, Object> argumentsAsMap() {
        return ToolExecutionRequestUtil.argumentsAsMap(arguments());
    }

    /**
     * The chat memory id of the invocation, or {@code null} when there is no memory.
     */
    default Object memoryId() {
        return invocationContext() == null ? null : invocationContext().chatMemoryId();
    }
}
