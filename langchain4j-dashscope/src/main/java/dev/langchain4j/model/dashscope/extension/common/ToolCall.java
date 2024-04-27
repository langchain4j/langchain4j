package dev.langchain4j.model.dashscope.extension.common;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ToolCall {
    private final String id;
    private final String type;
    private FunctionCall function;
}
