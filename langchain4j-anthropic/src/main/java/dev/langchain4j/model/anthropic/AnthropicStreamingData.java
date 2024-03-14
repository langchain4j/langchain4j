package dev.langchain4j.model.anthropic;

import lombok.Getter;

import java.util.List;

class AnthropicStreamingData {

    String type;

    // type = "message_start"
    Message message;

    // type = "content_block_start" || "content_block_delta" || "content_block_stop"
    Integer index;

    // type = "content_block_start"
    Content contentBlock;

    // type = "content_block_delta" || "message_delta"
    Delta delta; // mix of Content and Message

    // type = "message_delta"
    Usage usage;


    @Getter
    static class Message {

        String id;
        String type;
        String role;
        List<Object> content;
        String model;
        String stopReason;
        String stopSequence;
        Usage usage;
    }

    @Getter
    static class Content {

        String type;
        String text;
    }

    @Getter
    static class Delta {

        // AnthropicStreamingData.type = "content_block_delta"
        String type;
        String text;

        // AnthropicStreamingData.type = "message_delta"
        String stopReason;
        String stopSequence;
    }

    @Getter
    static class Usage {

        Integer inputTokens;
        Integer outputTokens;
    }
}