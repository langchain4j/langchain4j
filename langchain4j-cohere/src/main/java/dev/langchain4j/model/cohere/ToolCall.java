package dev.langchain4j.model.cohere;

import lombok.Builder;

import java.util.Map;

@Builder
public class ToolCall {
    String name;
    Map<String, String> parameters;
}
