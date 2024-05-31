package dev.langchain4j.model.anthropic.internal.sanitizer;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class MessageSanitizerTest {

    @Test
    void testSanitizeMessages_EmptyList_ThrowsException() {
        List<ChatMessage> messages = new ArrayList<>();
        assertThrows(IllegalArgumentException.class, () -> MessageSanitizer.sanitizeMessages(messages));
    }

    @Test
    void testSanitizeMessages_RemovesSystemMessages() {
        List<ChatMessage> messages = Arrays.asList(
                SystemMessage.from("System message 1"),
                UserMessage.from("User message 1"),
                SystemMessage.from("System message 2")
        );

        List<ChatMessage> sanitizedMessages = MessageSanitizer.sanitizeMessages(messages);

        assertEquals(1, sanitizedMessages.size());
        assertTrue(sanitizedMessages.get(0) instanceof UserMessage);
    }

    @Test
    void testSanitizeMessages_AddsUserMessageIfEmpty() {
        List<ChatMessage> messages = Arrays.asList(
                SystemMessage.from("System message 1")
        );

        List<ChatMessage> sanitizedMessages = MessageSanitizer.sanitizeMessages(messages);

        assertEquals(1, sanitizedMessages.size());
        assertTrue(sanitizedMessages.get(0) instanceof UserMessage);
    }

    @Test
    void testSanitizeMessages_AddsUserMessageIfFirstMessageNotUser() {
        List<ChatMessage> messages = Arrays.asList(
                SystemMessage.from("System message 1"),
                UserMessage.from("User message 1")
        );

        List<ChatMessage> sanitizedMessages = MessageSanitizer.sanitizeMessages(messages);

        assertEquals(1, sanitizedMessages.size());
        assertTrue(sanitizedMessages.get(0) instanceof UserMessage);
    }

}