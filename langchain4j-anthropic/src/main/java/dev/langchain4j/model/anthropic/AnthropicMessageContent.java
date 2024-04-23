package dev.langchain4j.model.anthropic;

import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public abstract class AnthropicMessageContent {

    public String type;

    public AnthropicMessageContent(String type) {
        this.type = type;
    }
}
