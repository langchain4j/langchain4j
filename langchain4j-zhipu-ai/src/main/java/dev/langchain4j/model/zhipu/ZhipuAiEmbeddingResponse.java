package dev.langchain4j.model.zhipu;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@Builder
@ToString
public class ZhipuAiEmbeddingResponse {
    private String model;
    private String object;
    private List<ZhipuAiEmbedding> data;
    private ZhipuAiUsage usage;
}
