package dev.langchain4j.model.anthropic;

import java.util.List;

class AnthropicCreateMessageResponse {

    String id;
    String type;
    String role;
    List<AnthropicContent> content;
    String model;
    String stopReason;
    String stopSequence;
    AnthropicUsage usage;
}
