package dev.langchain4j.model.anthropic;

class AnthropicStreamingData {

    String type;

    // type = "message_start"
    AnthropicResponseMessage message;

    // type = "content_block_start" || "content_block_delta" || "content_block_stop"
    Integer index;

    // type = "content_block_start"
    AnthropicContent contentBlock;

    // type = "content_block_delta" || "message_delta"
    AnthropicDelta delta; // mix of Content and Message

    // type = "message_delta"
    AnthropicUsage usage;
}