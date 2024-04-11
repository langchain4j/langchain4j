package dev.langchain4j.model.anthropic;

public class AnthropicStreamingData {

    public String type;

    // when type = "message_start"
    public AnthropicResponseMessage message;

    // when type = "content_block_start" || "content_block_delta" || "content_block_stop"
    public Integer index;

    // when type = "content_block_start"
    public AnthropicContent contentBlock;

    // when type = "content_block_delta" || "message_delta"
    public AnthropicDelta delta; // mix of Content and Message

    // when type = "message_delta"
    public AnthropicUsage usage;
}