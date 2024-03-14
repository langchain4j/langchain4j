package dev.langchain4j.model.anthropic;

import lombok.Getter;

import java.util.List;

@Getter
class AnthropicResponseMessage {

    String id;
    String type;
    String role;
    List<Object> content;
    String model;
    String stopReason;
    String stopSequence;
    AnthropicUsage usage;
}