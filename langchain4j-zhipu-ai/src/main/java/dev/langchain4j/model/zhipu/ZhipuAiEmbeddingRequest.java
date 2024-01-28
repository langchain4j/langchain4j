package dev.langchain4j.model.zhipu;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@ToString
public class ZhipuAiEmbeddingRequest {
    private String input;
    private ZhipuAiEmbeddingModelEnum model;
}
