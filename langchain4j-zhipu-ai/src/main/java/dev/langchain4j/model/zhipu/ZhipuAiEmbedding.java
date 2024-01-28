package dev.langchain4j.model.zhipu;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@Builder
@ToString
public class ZhipuAiEmbedding {
    private Integer index;
    private String object;
    private List<Float> embedding;
}
