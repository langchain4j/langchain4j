package dev.langchain4j.test.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Responses for the steps of a mock that reports token usage.
 */
final class TestResponses {

    private TestResponses() {}

    /**
     * A response with the given message and token usage, and the finish reason a provider reports for it.
     */
    static ChatResponse responseWithTokenUsage(AiMessage aiMessage, TokenUsage tokenUsage) {
        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .tokenUsage(tokenUsage)
                .finishReason(aiMessage.hasToolExecutionRequests() ? FinishReason.TOOL_EXECUTION : FinishReason.STOP)
                .build();
    }
}
