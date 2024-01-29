package dev.langchain4j.model.openai;

public enum OpenAiEmbeddingModelName {

    TEXT_EMBEDDING_3_SMALL("text-embedding-3-small"),
    TEXT_EMBEDDING_3_LARGE("text-embedding-3-large"),

    TEXT_EMBEDDING_ADA_002("text-embedding-ada-002");

    private final String stringValue;

    OpenAiEmbeddingModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
