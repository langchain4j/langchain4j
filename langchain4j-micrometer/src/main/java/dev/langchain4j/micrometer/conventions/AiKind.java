package dev.langchain4j.micrometer.conventions;

public enum AiKind {
    /**
     * Langchain4J kind for advisor.
     */
    ADVISOR("advisor"),

    /**
     * Langchain4J kind for chat client.
     */
    CHAT_CLIENT("chat_client"),

    /**
     * Langchain4J kind for vector store.
     */
    VECTOR_STORE("vector_store");

    private final String value;

    AiKind(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
