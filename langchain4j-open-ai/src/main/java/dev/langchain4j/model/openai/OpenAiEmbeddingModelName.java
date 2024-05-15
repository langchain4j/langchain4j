package dev.langchain4j.model.openai;

import java.util.HashMap;
import java.util.Map;

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

    public static Map<String, Integer> embeddingModelDimensionMap() {
        return new HashMap<String, Integer>() {{
            put(TEXT_EMBEDDING_ADA_002.toString(), 1536);
            put(TEXT_EMBEDDING_3_SMALL.toString(), 1536);
            put(TEXT_EMBEDDING_3_LARGE.toString(), 3072);
        }};
    }
}
