package dev.langchain4j.micrometer.metrics.conventions;

/**
 * Observation metric names for generative AI client metrics
 * in line with the OpenTelemetry Semantic Conventions for Generative AI Metrics.
 * v1.39.0
 * <p>
 * The agent and tool metrics follow these conventions as of commit {@code da2b5736} of the
 * {@code open-telemetry/semantic-conventions-genai} repository.
 */
public enum OTelGenAiMetricName {
    /**
     * GenAI operation duration.
     */
    OPERATION_DURATION("gen_ai.client.operation.duration"),

    /**
     * Measures number of input and output tokens used
     */
    TOKEN_USAGE("gen_ai.client.token.usage"),

    /**
     * Duration of the invocation of an agent, or of a workflow coordinating several agents.
     */
    INVOKE_AGENT_DURATION("gen_ai.invoke_agent.duration"),

    /**
     * Number of model calls made by one invocation of an agent.
     */
    INVOKE_AGENT_INFERENCE_CALLS("gen_ai.invoke_agent.inference_calls"),

    /**
     * Number of tool calls made by one invocation of an agent.
     */
    INVOKE_AGENT_TOOL_CALLS("gen_ai.invoke_agent.tool_calls"),

    /**
     * Duration of the execution of a tool.
     */
    EXECUTE_TOOL_DURATION("gen_ai.execute_tool.duration");

    private final String value;

    OTelGenAiMetricName(String value) {
        this.value = value;
    }

    public String value() {
        return this.value;
    }
}
