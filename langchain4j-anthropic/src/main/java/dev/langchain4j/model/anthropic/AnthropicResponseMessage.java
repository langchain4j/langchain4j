package dev.langchain4j.model.anthropic;

import java.util.List;

public class AnthropicResponseMessage {

    String id;
    String type;
    String role;
    List<Object> content;
    String model;
    String stopReason;
    String stopSequence;
    AnthropicUsage usage;
}