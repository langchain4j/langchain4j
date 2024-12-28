package dev.langchain4j.micrometer.conventions;

// Copied from AiObservationMetricNames.java in spring-ai-core
public enum AiObservationMetricNames {
    /**
     * The duration of the AI operation.
     */
    OPERATION_DURATION("gen_ai.client.operation.duration"),
    /**
     * The number of AI operations.
     */
    TOKEN_USAGE("gen_ai.client.token.usage");

    private final String value;

    AiObservationMetricNames(String value) {
        this.value = value;
    }

    /**
     * Return the value of the metric name.
     * @return the value of the metric name
     */
    public String value() {
        return this.value;
    }
}
