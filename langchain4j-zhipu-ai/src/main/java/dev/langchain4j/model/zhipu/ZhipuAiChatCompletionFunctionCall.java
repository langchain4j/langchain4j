package dev.langchain4j.model.zhipu;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
@ToString
class ZhipuAiChatCompletionFunctionCall {
    private String name;
    private String arguments;
}
