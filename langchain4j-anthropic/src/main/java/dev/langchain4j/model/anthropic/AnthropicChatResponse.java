package dev.langchain4j.model.anthropic;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
class AnthropicChatResponse {

    private List<Content> content;
    private String id;
    private String model;
    private String role;
    private String stopReason;
    private String stopSequence;
    private String type;
    private Usage usage;

    @Getter
    @Builder
    static class Content {
        private String text;
        private String type;
    }

    @Getter
    @Builder
    static class Usage {
        private int inputTokens;
        private int outputTokens;
    }
}
