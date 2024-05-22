package dev.langchain4j.model.cohere.internal.api;

import lombok.Builder;

import java.util.List;
import java.util.Map;

@Builder
public class ToolResult {

    ToolCall call;
    List<Map<String, String>> outputs;
}
