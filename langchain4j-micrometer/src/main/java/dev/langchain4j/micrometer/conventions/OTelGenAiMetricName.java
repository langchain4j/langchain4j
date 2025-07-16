package dev.langchain4j.micrometer.conventions;

/**
 * Observation metric names for generative AI client metrics
 * in line with the OpenTelemetry Semantic Conventions for Generative AI Metrics.
 */
public enum OTelGenAiMetricName {
    /**
     * GenAI operation duration.
     */
    OPERATION_DURATION("gen_ai.client.operation.duration"),

    /**
     * Measures number of input and output tokens used
     */
    TOKEN_USAGE("gen_ai.client.token.usage");

    private final String value;

    OTelGenAiMetricName(String value) {
        this.value = value;
    }

    public String value() {
        return this.value;
    }
}
