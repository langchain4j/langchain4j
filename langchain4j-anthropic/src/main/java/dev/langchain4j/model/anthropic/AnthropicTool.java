package dev.langchain4j.model.anthropic;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Builder
@ToString
@EqualsAndHashCode
public class AnthropicTool {

    public String name;
    public String description;
    public AnthropicToolSchema inputSchema;
}
