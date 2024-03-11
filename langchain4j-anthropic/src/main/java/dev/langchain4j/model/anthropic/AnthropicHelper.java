package dev.langchain4j.model.anthropic;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;

import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.stream.Collectors.toList;

public class AnthropicHelper {

    static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/";

    static List<AnthropicChatMessage> toAnthropicAiMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(AnthropicHelper::toAnthropicAiMessage)
                .collect(toList());
    }

    static AnthropicChatMessage toAnthropicAiMessage(ChatMessage message) {
        return AnthropicChatMessage.builder()
                .role(toAnthropicAiRole(message.type()))
                .content(toAnthropicChatMessageContent(message))
                .build();
    }

    private static AnthropicRole toAnthropicAiRole(ChatMessageType chatMessageType) {
        switch (chatMessageType) {
            case AI:
                return AnthropicRole.ASSISTANT;
            case USER:
                return AnthropicRole.USER;
            case SYSTEM:
            default:
                throw new IllegalArgumentException("Unknown chat message type: " + chatMessageType);
        }
    }

    private static String toAnthropicChatMessageContent(ChatMessage message) {
        if (message instanceof SystemMessage) {
            //TODO
        }

        if (message instanceof AiMessage) {
            return ((AiMessage) message).text();
        }

        if (message instanceof UserMessage) {
            return message.text();
        }

        throw new IllegalArgumentException("Unknown message type: " + message.type());
    }

    static TokenUsage tokenUsageFrom(AnthropicChatResponse.Usage anthropicAiUsage) {
        if (anthropicAiUsage == null) {
            return null;
        }
        return new TokenUsage(
                anthropicAiUsage.getInputTokens(),
                anthropicAiUsage.getOutputTokens(),
                anthropicAiUsage.getInputTokens() + anthropicAiUsage.getOutputTokens()
        );
    }

    static FinishReason finishReasonFrom(String anthropicAiFinishReason) {
        if (anthropicAiFinishReason == null) {
            return null;
        }
        switch (anthropicAiFinishReason) {
            case "end_turn":
            case "stop_sequence": //TODO
                return STOP;
            case "max_tokens":
                return LENGTH;
            default:
                return null; //TODO or throw IllegalStateException?
        }
    }

}
