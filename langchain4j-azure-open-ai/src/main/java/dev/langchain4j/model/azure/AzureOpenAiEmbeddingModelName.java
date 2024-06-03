package dev.langchain4j.model.azure;

public enum AzureOpenAiEmbeddingModelName {

    TEXT_EMBEDDING_3_SMALL("text-embedding-3-small"), // alias for the latest text-embedding-3-small model
    TEXT_EMBEDDING_3_SMALL_1("text-embedding-3-small-1"),
    TEXT_EMBEDDING_3_LARGE("text-embedding-3-large"),
    TEXT_EMBEDDING_3_LARGE_1("text-embedding-3-large-1"),

    TEXT_EMBEDDING_ADA_002("text-embedding-ada-002"), // alias for the latest text-embedding-ada-002 model
    TEXT_EMBEDDING_ADA_002_1("text-embedding-ada-002-1"),
    TEXT_EMBEDDING_ADA_002_2("text-embedding-ada-002-2");

    private final String stringValue;

    AzureOpenAiEmbeddingModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
