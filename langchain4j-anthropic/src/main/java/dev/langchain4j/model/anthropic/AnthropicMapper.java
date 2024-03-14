package dev.langchain4j.model.anthropic;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;

import static dev.langchain4j.data.message.ChatMessageType.SYSTEM;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.model.output.FinishReason.*;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

class AnthropicMapper {

    static List<AnthropicMessage> toAnthropicMessages(List<ChatMessage> messages) {
        return messages.stream()
                .filter(message -> message.type() != SYSTEM)
                .map(AnthropicMapper::toAnthropicMessage)
                .collect(toList());
    }

    static AnthropicMessage toAnthropicMessage(ChatMessage message) {
        return AnthropicMessage.builder()
                .role(toAnthropicRole(message.type()))
                .content(toAnthropicContent(message))
                .build();
    }

    static String toAnthropicSystemPrompt(List<ChatMessage> messages) {
        String systemPrompt = messages.stream()
                .filter(message -> message instanceof SystemMessage)
                .map(message -> ((SystemMessage) message).text())
                .collect(joining("\n\n"));

        if (isNullOrBlank(systemPrompt)) {
            return null;
        } else {
            return systemPrompt;
        }
    }

    private static AnthropicRole toAnthropicRole(ChatMessageType chatMessageType) {
        switch (chatMessageType) {
            case AI:
                return AnthropicRole.ASSISTANT;
            case USER:
                return AnthropicRole.USER;
            default:
                throw new IllegalArgumentException("Unknown chat message type: " + chatMessageType);
        }
    }

    private static Object toAnthropicContent(ChatMessage message) {
        if (message instanceof AiMessage) {
            AiMessage aiMessage = (AiMessage) message;
            return aiMessage.text();
        } else if (message instanceof UserMessage) {
            UserMessage userMessage = (UserMessage) message;
            return userMessage.contents().stream()
                    .map(content -> {
                        if (content instanceof TextContent) {
                            return new AnthropicTextContent(((TextContent) content).text());
                        } else if (content instanceof ImageContent) {
                            Image image = ((ImageContent) content).image();
                            if (image.url() != null) {
                                throw illegalArgument("Anthropic does not support images as URLs, " +
                                        "only as Base64-encoded strings");
                            }
                            return new AnthropicImageContent(
                                    ensureNotBlank(image.mimeType(), "mimeType"),
                                    ensureNotBlank(image.base64Data(), "base64Data")
                            );
                        } else {
                            throw illegalArgument("Unknown content type: " + content);
                        }
                    }).collect(toList());
        } else {
            throw new IllegalArgumentException("Unknown message type: " + message.type());
        }
    }

    static AiMessage toAiMessage(List<AnthropicContent> contents) {
        String text = contents.stream()
                .filter(content -> "text".equals(content.getType()))
                .map(AnthropicContent::getText)
                .collect(joining("\n"));
        return AiMessage.from(text);
    }

    static TokenUsage toTokenUsage(AnthropicUsage anthropicUsage) {
        if (anthropicUsage == null) {
            return null;
        }
        return new TokenUsage(anthropicUsage.getInputTokens(), anthropicUsage.getOutputTokens());
    }

    static FinishReason toFinishReason(String anthropicStopReason) {
        if (anthropicStopReason == null) {
            return null;
        }
        switch (anthropicStopReason) {
            case "end_turn":
                return STOP;
            case "max_tokens":
                return LENGTH;
            case "stop_sequence":
                return OTHER; // TODO
            default:
                return null; // TODO
        }
    }
}
