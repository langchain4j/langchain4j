package dev.langchain4j.micrometer.conventions;

public enum AiOperationType {
    /**
     * AI operation type for chat.
     */
    CHAT("chat"),

    /**
     * AI operation type for embedding.
     */
    EMBEDDING("embedding"),

    /**
     * AI operation type for framework.
     */
    FRAMEWORK("framework"),

    /**
     * AI operation type for image.
     */
    IMAGE("image"),

    /**
     * AI operation type for text completion.
     */
    TEXT_COMPLETION("text_completion");

    private final String value;

    AiOperationType(String value) {
        this.value = value;
    }

    /**
     * Return the value of the operation type.
     * @return the value of the operation type
     */
    public String value() {
        return this.value;
    }
}
