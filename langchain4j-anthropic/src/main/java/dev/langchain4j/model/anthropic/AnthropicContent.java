package dev.langchain4j.model.anthropic;

import lombok.Builder;

import java.util.Map;

@Builder
public class AnthropicContent {

    public String type;

    // when type = "text"
    public String text;

    // when type = "tool_use"
    public String id;
    public String name;
    public Map<String, Object> input;
}