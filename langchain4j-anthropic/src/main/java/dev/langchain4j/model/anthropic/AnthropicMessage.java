package dev.langchain4j.model.anthropic;

import lombok.Builder;

@Builder
public class AnthropicMessage {

    AnthropicRole role;
    Object content;
}
