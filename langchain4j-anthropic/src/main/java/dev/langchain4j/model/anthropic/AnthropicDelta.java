package dev.langchain4j.model.anthropic;

class AnthropicDelta {

    // when AnthropicStreamingData.type = "content_block_delta"
    String type;
    String text;

    // when AnthropicStreamingData.type = "message_delta"
    String stopReason;
    String stopSequence;
}