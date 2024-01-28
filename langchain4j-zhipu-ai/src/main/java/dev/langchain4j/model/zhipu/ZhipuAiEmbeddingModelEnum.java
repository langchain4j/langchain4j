package dev.langchain4j.model.zhipu;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

@Getter
public enum ZhipuAiEmbeddingModelEnum {
    @SerializedName("embedding-2") EMBEDDING_2,
    @SerializedName("text_embedding") TEXT_EMBEDDING,
    ;
}
