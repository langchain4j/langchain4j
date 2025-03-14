package dev.langchain4j.micrometer.conventions;

/**
 * Metric attribute for operation type of AI operations. The values of this enum are
 * inline with the OpenTelemetry Semantic Conventions for Generative AI Metrics.
 */
public enum OTelGenAiOperationName {
    /**
     * AI operation type for chat.
     */
    CHAT("chat"),

    /**
     * AI operation type for text completion.
     */
    TEXT_COMPLETION("text_completion"),

    /**
     * AI operation type for embeddings.
     */
    EMBEDDINGS("embeddings");

    private final String value;

    OTelGenAiOperationName(String value) {
        this.value = value;
    }

    public String value() {
        return this.value;
    }
}
