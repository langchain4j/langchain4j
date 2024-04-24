package dev.langchain4j.model.zhipu;

import com.zhipu.oapi.Constants;

public enum ZhipuAiEmbeddingModelName {

    EMBEDDING_2(Constants.ModelEmbedding2),
    ;

    private final String stringValue;

    ZhipuAiEmbeddingModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
