package dev.langchain4j.model.zhipu;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@ToString
class ZhipuAiChatCompletionToolCall {
    private String id;
    private String index;
    private String type;
    private ZhipuAiChatCompletionFunctionCall function;
}
