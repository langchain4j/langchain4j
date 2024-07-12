package dev.langchain4j.model.anthropic.internal.sanitizer;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

/**
 * Sanitizes the messages to conform to the format expected by the Anthropic API.
 */
@Slf4j
public class MessageSanitizer {


    public static List<ChatMessage> sanitizeMessages(List<ChatMessage> messages) {
        ensureNotEmpty(messages, "messages");
        List<ChatMessage> sanitizedMessages = new ArrayList<>(messages);
        stripSystemMessages(sanitizedMessages);
        ensureFirstMessageIsUserMessage(sanitizedMessages);
        ensureNoConsecutiveUserMessages(sanitizedMessages);

        return sanitizedMessages;
    }

    private static void stripSystemMessages(List<ChatMessage> messages) {
        messages.removeIf(message -> message instanceof SystemMessage);
    }

    private static void ensureNoConsecutiveUserMessages(List<ChatMessage> messages) {
        boolean lastWasUserMessage = false;
        List<ChatMessage> toRemove = new ArrayList<>();

        for (ChatMessage message : messages) {
            if (message instanceof UserMessage) {
                if (lastWasUserMessage) {
                    toRemove.add(message);
                    log.warn("Removing consecutive UserMessage: {}", ((UserMessage) message).singleText());
                } else {
                    lastWasUserMessage = true;
                }
            } else {
                lastWasUserMessage = false;
            }
        }

        messages.removeAll(toRemove);
    }

    private static void ensureFirstMessageIsUserMessage(List<ChatMessage> messages) {
        while (!messages.isEmpty() && !(messages.get(0) instanceof UserMessage)) {
            ChatMessage removedMessage = messages.remove(0);
            log.warn("Dropping non-UserMessage in 1st element: {}", removedMessage);
        }
    }

}
