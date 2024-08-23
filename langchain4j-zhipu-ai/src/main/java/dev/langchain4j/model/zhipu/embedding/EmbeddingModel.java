package dev.langchain4j.model.zhipu.embedding;

public enum EmbeddingModel {

    EMBEDDING_2("embedding-2"),
    EMBEDDING_3("embedding-3"),
    TEXT_EMBEDDING("text_embedding"),
    ;

    private final String value;

    EmbeddingModel(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }
}
