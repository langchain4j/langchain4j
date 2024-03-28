package dev.langchain4j.model.anthropic;

import lombok.Builder;

import java.util.List;

@Builder
public class AnthropicCreateMessageRequest {

    String model;
    List<AnthropicMessage> messages;
    String system;
    int maxTokens;
    List<String> stopSequences;
    boolean stream;
    Double temperature;
    Double topP;
    Integer topK;
}
