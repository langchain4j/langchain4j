package dev.langchain4j.model.anthropic;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
class AnthropicContent {

    private final String type;
    private final String text;
}