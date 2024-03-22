package dev.langchain4j.model.anthropic;

import lombok.Builder;

@Builder
class AnthropicMessage {

    AnthropicRole role;
    Object content;
}
