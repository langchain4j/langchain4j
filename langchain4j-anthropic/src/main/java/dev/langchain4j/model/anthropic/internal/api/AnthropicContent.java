package dev.langchain4j.model.anthropic.internal.api;

import java.util.Map;

public class AnthropicContent {

    public String type;

    // when type = "text"
    public String text;

    // when type = "tool_use"
    public String id;
    public String name;
    public Map<String, Object> input;
}