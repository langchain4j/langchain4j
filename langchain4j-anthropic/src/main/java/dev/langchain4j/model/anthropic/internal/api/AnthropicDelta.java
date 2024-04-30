package dev.langchain4j.model.anthropic.internal.api;

public class AnthropicDelta {

    // when AnthropicStreamingData.type = "content_block_delta"
    public String type;
    public String text;

    // when AnthropicStreamingData.type = "message_delta"
    public String stopReason;
    public String stopSequence;
}