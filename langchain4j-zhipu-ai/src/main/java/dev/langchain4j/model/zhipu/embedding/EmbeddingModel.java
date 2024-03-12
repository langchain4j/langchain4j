package dev.langchain4j.model.zhipu.embedding;

public enum EmbeddingModel {

    EMBEDDING_2("embedding-2"),
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
