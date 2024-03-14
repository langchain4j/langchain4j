package dev.langchain4j.model.anthropic;

import lombok.Builder;

@Builder
class AnthropicMessage {

    private final AnthropicRole role;
    private final Object content;
}
