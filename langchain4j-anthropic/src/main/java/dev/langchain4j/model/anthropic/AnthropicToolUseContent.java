package dev.langchain4j.model.anthropic;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Map;

@ToString
@EqualsAndHashCode(callSuper = true)
public class AnthropicToolUseContent extends AnthropicMessageContent {

    public String id;
    public String name;
    public Map<String, Object> input;

    @Builder
    public AnthropicToolUseContent(String id, String name, Map<String, Object> input) {
        super("tool_use");
        this.id = id;
        this.name = name;
        this.input = input;
    }
}
