package dev.langchain4j.model.anthropic;

import lombok.Builder;
import lombok.Getter;
import okhttp3.internal.ws.RealWebSocket;

import java.util.List;

@Getter
@Builder
class AnthropicCreateMessageResponse {

    private final String id;
    private final String type;
    private final String role;
    private final List<Content> content;
    private final String model;
    private final String stopReason;
    private final String stopSequence;
    private final Usage usage;
    private final Message message;
    private final Delta delta;

    @Getter
    @Builder
    static class Content {

        private final String type;
        private final String text;
    }

    @Getter
    @Builder
    static class Usage {

        private final int inputTokens;
        private final int outputTokens;
    }

    @Getter
    @Builder
    static class Delta {
        private final String type;
        private final String  text;
    }

    @Getter
    @Builder
    static class Message {
        private final Usage usage;
    }
}
