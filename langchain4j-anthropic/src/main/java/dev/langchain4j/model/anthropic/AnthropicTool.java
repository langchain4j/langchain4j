package dev.langchain4j.model.anthropic;

import lombok.Builder;

@Builder
public class AnthropicTool {

    public String name;
    public String description;
    public AnthropicToolSchema inputSchema;
}
