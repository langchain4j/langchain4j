package dev.langchain4j.model.anthropic;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
class AnthropicCreateMessageResponse {

    private final String id;
    private final String type;
    private final String role;
    private final List<AnthropicContent> content;
    private final String model;
    private final String stopReason;
    private final String stopSequence;
    private final AnthropicUsage usage;
}
