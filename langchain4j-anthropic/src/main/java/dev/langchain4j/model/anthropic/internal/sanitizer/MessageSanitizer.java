package dev.langchain4j.model.anthropic.internal.sanitizer;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

public class MessageSanitizer {

    private static final UserMessage DUMMY_USER_MESSAGE = UserMessage.from(".");

    public static List<ChatMessage> sanitizeMessages(List<ChatMessage> messages) {
        ensureNotEmpty(messages, "messages");

        List<ChatMessage> sanitizedMessages = messages.stream()
                .filter(message -> !(message instanceof SystemMessage))
                .collect(Collectors.toList());

        if (sanitizedMessages.isEmpty() || !(sanitizedMessages.get(0) instanceof UserMessage)) {
            sanitizedMessages.add(0, DUMMY_USER_MESSAGE);
        }

        return sanitizedMessages;
    }

}
