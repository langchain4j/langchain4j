package dev.langchain4j.micrometer.conventions;

/**
 * Metric attribute for AI observation metrics.
 * The values are in line with the OpenTelemetry Semantic Conventions for Generative AI Metrics.
 */
public enum OTelGenAiMetricAttributes {

    /**
     * The name of the operation being performed.
     */
    OPERATION_NAME("gen_ai.operation.name"),
    /**
     * The model provider as identified by the client instrumentation.
     */
    SYSTEM("gen_ai.system"),

    /**
     * The type of token that is counted: input, output, total.
     */
    TOKEN_TYPE("gen_ai.token.type"),

    /**
     * The name of the model a request is being made to.
     */
    REQUEST_MODEL("gen_ai.request.model"),

    /**
     * The name of the model that generated the response.
     */
    RESPONSE_MODEL("gen_ai.response.model"),

    /**
     * The class of error the operation ended with.
     */
    ERROR_TYPE("error.type"),

    /**
     * The GenAI server port.
     */
    SERVER_PORT("server.port"),

    /**
     * The GenAI server address.
     */
    SERVER_ADDRESS("server.address");

    private final String value;

    OTelGenAiMetricAttributes(String value) {
        this.value = value;
    }

    public String value() {
        return this.value;
    }
}
