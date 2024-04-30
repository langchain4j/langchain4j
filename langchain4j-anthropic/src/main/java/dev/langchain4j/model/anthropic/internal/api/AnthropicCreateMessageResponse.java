package dev.langchain4j.model.anthropic.internal.api;

import java.util.List;

public class AnthropicCreateMessageResponse {

    public String id;
    public String type;
    public String role;
    public List<AnthropicContent> content;
    public String model;
    public String stopReason;
    public String stopSequence;
    public AnthropicUsage usage;
}
