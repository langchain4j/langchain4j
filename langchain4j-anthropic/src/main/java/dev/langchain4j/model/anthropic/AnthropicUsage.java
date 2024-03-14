package dev.langchain4j.model.anthropic;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
class AnthropicUsage {

    private final Integer inputTokens;
    private final Integer outputTokens;
}