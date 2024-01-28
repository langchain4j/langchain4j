package dev.langchain4j.model.zhipu;

import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@ToString
class ZhipuAiChatCompletionDelta {
    private String content;
    private List<ZhipuAiChatCompletionToolCall> toolCalls;
}
