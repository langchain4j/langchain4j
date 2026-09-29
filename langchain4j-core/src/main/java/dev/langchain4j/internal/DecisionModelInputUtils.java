package dev.langchain4j.internal;

import static java.util.stream.Collectors.joining;

import dev.langchain4j.Internal;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts chat messages into the input of a {@link dev.langchain4j.model.decision.DecisionModel}, the same way for
 * all components that use one.
 */
@Internal
public class DecisionModelInputUtils {

    private DecisionModelInputUtils() {}

    /**
     * The text of a user or assistant message. Content of a user message other than text is represented by a marker,
     * such as {@code [attached image]}. Other messages, and assistant messages without text, give an empty string.
     */
    public static String text(ChatMessage message) {
        if (message instanceof UserMessage userMessage) {
            return userMessage.contents().stream()
                    .map(content -> content instanceof TextContent textContent
                            ? textContent.text()
                            : "[attached " + content.type().name().toLowerCase(Locale.ROOT) + "]")
                    .collect(joining("\n"));
        }
        if (message instanceof AiMessage aiMessage && aiMessage.text() != null) {
            return aiMessage.text();
        }
        return "";
    }

    /**
     * Whether the user message contains text, not only other content such as images.
     */
    public static boolean hasText(UserMessage userMessage) {
        return userMessage.contents().stream()
                .anyMatch(content -> content instanceof TextContent textContent
                        && !textContent.text().isBlank());
    }

    /**
     * The user and assistant messages with {@link #text(ChatMessage) text}, as {@code {"role": "user" | "assistant",
     * "text": ...}} maps.
     */
    public static List<Map<String, String>> messages(List<ChatMessage> messages) {
        List<Map<String, String>> result = new ArrayList<>();
        for (ChatMessage message : messages) {
            String text = text(message);
            if (!text.isBlank()) {
                result.add(Map.of("role", message instanceof UserMessage ? "user" : "assistant", "text", text));
            }
        }
        return result;
    }
}
