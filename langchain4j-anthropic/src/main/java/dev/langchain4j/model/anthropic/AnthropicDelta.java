package dev.langchain4j.model.anthropic;

import lombok.Getter;

@Getter
class AnthropicDelta {

    // AnthropicStreamingData.type = "content_block_delta"
    String type;
    String text;

    // AnthropicStreamingData.type = "message_delta"
    String stopReason;
    String stopSequence;
}