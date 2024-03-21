package dev.langchain4j.model.dashscope.extension.common;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FunctionCall {
    private final String name;
    private final String arguments;
}
