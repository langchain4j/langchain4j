package dev.langchain4j.model.zhipu;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@Builder
@ToString
class ZhipuAiChatCompletionFunctionParameters {
    private String type;
    private Object properties;
    private List<String> required;
}