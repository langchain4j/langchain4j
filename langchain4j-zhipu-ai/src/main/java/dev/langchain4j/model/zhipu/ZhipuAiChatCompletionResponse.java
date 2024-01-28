package dev.langchain4j.model.zhipu;

import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString
public final class ZhipuAiChatCompletionResponse {
    private String id;
    private Long created;
    private String model;
    private List<ZhipuAiChatCompletionChoice> choices;
    private ZhipuAiUsage usage;
}
