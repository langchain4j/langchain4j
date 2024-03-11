package dev.langchain4j.model.anthropic;

import lombok.Builder;

import java.util.List;

@Builder
class AnthropicCreateMessageRequest {

    private final String model;
    private final List<AnthropicMessage> messages;
    private final String system;
    private final int maxTokens;
    private final List<String> stopSequences;
    private final Boolean stream;
    private final Double temperature;
    private final Double topP;
    private final Integer topK;
}
