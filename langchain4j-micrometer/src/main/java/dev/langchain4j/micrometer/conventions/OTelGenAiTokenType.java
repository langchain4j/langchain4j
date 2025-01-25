package dev.langchain4j.micrometer.conventions;

/**
 * Metric attribute for token type of AI operations that is counted.
 * The values are in line with the OpenTelemetry Semantic Conventions for Generative AI Metrics.
 */
public enum OTelGenAiTokenType {
    /**
     * Input token.
     */
    INPUT("input"),
    /**
     * Output token.
     */
    OUTPUT("output");

    private final String value;

    OTelGenAiTokenType(String value) {
        this.value = value;
    }

    public String value() {
        return this.value;
    }
}
