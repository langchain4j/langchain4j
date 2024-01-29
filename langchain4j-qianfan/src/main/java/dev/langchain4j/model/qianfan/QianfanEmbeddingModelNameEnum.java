package dev.langchain4j.model.qianfan;

import lombok.Getter;

@Getter
public enum QianfanEmbeddingModelNameEnum {

    EMBEDDING_V1("Embedding-V1", "embedding-v1"),
    BGE_LARGE_ZH("bge-large-zh", "bge_large_zh"),
    BGE_LARGE_EN("bge-large-en", "bge_large_en"),
    TAO_8K("tao-8k", "tao_8k")
    ;

    private String modelName;

    private String endpoint;

    QianfanEmbeddingModelNameEnum(String modelName, String endpoint) {
        this.modelName = modelName;
        this.endpoint = endpoint;
    }


    public static String getEndpoint(String modelName) {
        for (QianfanEmbeddingModelNameEnum qianfanEmbeddingModelNameEnum : QianfanEmbeddingModelNameEnum.values()) {
            if (qianfanEmbeddingModelNameEnum.getModelName().equals(modelName)) {
                return qianfanEmbeddingModelNameEnum.getEndpoint();
            }
        }
        return null;
    }
}
