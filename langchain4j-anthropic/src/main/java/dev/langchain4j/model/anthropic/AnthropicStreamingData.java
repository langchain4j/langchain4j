package dev.langchain4j.model.anthropic;

class AnthropicStreamingData {

    String type;

    // when type = "message_start"
    AnthropicResponseMessage message;

    // when type = "content_block_start" || "content_block_delta" || "content_block_stop"
    Integer index;

    // when type = "content_block_start"
    AnthropicContent contentBlock;

    // when type = "content_block_delta" || "message_delta"
    AnthropicDelta delta; // mix of Content and Message

    // when type = "message_delta"
    AnthropicUsage usage;
}