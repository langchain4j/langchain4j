package dev.langchain4j.micrometer.conventions;

// Copied from AiObservationMetricAttributes.java in spring-ai-core
public enum AiObservationMetricAttributes {
    /**
     * The type of token being counted (input, output, total).
     */
    TOKEN_TYPE("gen_ai.token.type");

    private final String value;

    AiObservationMetricAttributes(String value) {
        this.value = value;
    }

    /**
     * Return the value of the metric attribute.
     * @return the value of the metric attribute
     */
    public String value() {
        return this.value;
    }
}
