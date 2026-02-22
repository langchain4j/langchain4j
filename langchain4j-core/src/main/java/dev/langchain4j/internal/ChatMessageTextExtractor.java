package dev.langchain4j.internal;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class responsible for extracting moderatable text
 * from various ChatMessage implementations.
 *
 * This keeps ModerationModel low-level ({@code String}/{@code List<String>} only)
 * and centralizes message-to-text conversion logic.
 */
public final class ChatMessageTextExtractor {

    private ChatMessageTextExtractor() {}

    /**
     * Extracts all relevant text parts from a ChatMessage.
     *
     * - UserMessage: all TextContent parts
     * - AiMessage:  {text + thinking + toolExecutionRequests}
     *
     * @param message ChatMessage to extract from
     * @return List of strings to moderate (empty if none)
     */
    public static List<String> extract(ChatMessage message) {

        if (message == null) {
            return List.of();
        }

        if (message instanceof UserMessage userMessage) {
            return extractFromUser(userMessage);
        }

        if (message instanceof AiMessage aiMessage) {
            return extractFromAi(aiMessage);
        }

        // Other message types (SystemMessage, ToolExecutionResultMessage, etc.)
        // Currently ignored for moderation

        return List.of();
    }

    private static List<String> extractFromUser(UserMessage userMessage) {

        return userMessage.contents().stream()
                .filter(TextContent.class::isInstance)
                .map(TextContent.class::cast)
                .map(TextContent::text)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.toList());
    }

    private static List<String> extractFromAi(AiMessage aiMessage) {

        List<String> texts = new ArrayList<>();

        if (aiMessage.text() != null && !aiMessage.text().isBlank()) {
            texts.add(aiMessage.text());
        }

        if (aiMessage.thinking() != null && !aiMessage.thinking().isBlank()) {
            texts.add(aiMessage.thinking());
        }

        if (aiMessage.toolExecutionRequests() != null) {
            for (ToolExecutionRequest request : aiMessage.toolExecutionRequests()) {

                if (request.arguments() != null) {
                    String argsText = request.arguments();
                    if (!argsText.isBlank()) {
                        texts.add(argsText);
                    }
                }
            }
        }

        return texts;
    }
}
