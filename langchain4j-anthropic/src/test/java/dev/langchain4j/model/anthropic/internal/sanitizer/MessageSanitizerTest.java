package dev.langchain4j.model.anthropic.internal.sanitizer;

import static org.junit.jupiter.api.Assertions.*;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import dev.langchain4j.data.message.AiMessage;

class MessageSanitizerTest {

    @Test
    void test_stripSystemMessage() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("System message"));
        messages.add(new UserMessage("User message"));

        List<ChatMessage> sanitized = MessageSanitizer.sanitizeMessages(messages);

        assertEquals(1, sanitized.size());
        assertTrue(sanitized.get(0) instanceof UserMessage);
    }

    @Test
    void test_stripMultipleSystemMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("System message 1"));
        messages.add(new SystemMessage("System message 2"));
        messages.add(new UserMessage("User message"));

        List<ChatMessage> sanitized = MessageSanitizer.sanitizeMessages(messages);

        assertEquals(1, sanitized.size());
        assertTrue(sanitized.get(0) instanceof UserMessage);
    }

    @Test
    void test_removeSinglePairOfConsecutiveUserMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new UserMessage("User message 1"));
        messages.add(new UserMessage("User message 2"));
        messages.add(new AiMessage("AI message"));

        List<ChatMessage> sanitized = MessageSanitizer.sanitizeMessages(messages);

        assertEquals(2, sanitized.size());
        assertTrue(sanitized.get(0) instanceof UserMessage);
        assertTrue(sanitized.get(1) instanceof AiMessage);
    }

    @Test
    void test_removeMultiplePairsOfConsecutiveUserMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new UserMessage("User message 1"));
        messages.add(new UserMessage("User message 2"));
        messages.add(new AiMessage("AI message 1"));
        messages.add(new UserMessage("User message 3"));
        messages.add(new UserMessage("User message 4"));
        messages.add(new AiMessage("AI message 2"));

        List<ChatMessage> sanitized = MessageSanitizer.sanitizeMessages(messages);

        assertEquals(4, sanitized.size());
        assertTrue(sanitized.get(0) instanceof UserMessage);
        assertTrue(sanitized.get(1) instanceof AiMessage);
        assertTrue(sanitized.get(2) instanceof UserMessage);
        assertTrue(sanitized.get(3) instanceof AiMessage);
    }

    @Test
    void test_aiMessageAfterUserMessage() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new UserMessage("User message"));
        messages.add(new AiMessage("AI message"));

        List<ChatMessage> sanitized = MessageSanitizer.sanitizeMessages(messages);

        assertEquals(2, sanitized.size());
        assertTrue(sanitized.get(0) instanceof UserMessage);
        assertTrue(sanitized.get(1) instanceof AiMessage);
    }

    @Test
    void test_firstMessageIsUserMessage_noChange() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new UserMessage("User message"));
        messages.add(new AiMessage("AI message"));

        List<ChatMessage> sanitized = MessageSanitizer.sanitizeMessages(messages);

        assertEquals(2, sanitized.size());
        assertTrue(sanitized.get(0) instanceof UserMessage);
        assertTrue(sanitized.get(1) instanceof AiMessage);
    }

    @Test
    void test_firstMessageIsSystemMessage() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("System message"));
        messages.add(new UserMessage("User message"));
        messages.add(new AiMessage("AI message"));

        List<ChatMessage> sanitized = MessageSanitizer.sanitizeMessages(messages);

        assertEquals(2, sanitized.size());
        assertTrue(sanitized.get(0) instanceof UserMessage);
        assertTrue(sanitized.get(1) instanceof AiMessage);
    }

    @Test
    void test_firstMessageIsAiMessage() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new AiMessage("AI message"));
        messages.add(new UserMessage("User message"));
        messages.add(new AiMessage("AI message"));

        List<ChatMessage> sanitized = MessageSanitizer.sanitizeMessages(messages);

        assertEquals(2, sanitized.size());
        assertTrue(sanitized.get(0) instanceof UserMessage);
        assertTrue(sanitized.get(1) instanceof AiMessage);
    }

    @Test
    void test_invalidStartingMessageWithInvalidUserPair() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new AiMessage("AI message"));
        messages.add(new UserMessage("User message 1"));
        messages.add(new UserMessage("User message 2"));
        messages.add(new AiMessage("AI message"));

        List<ChatMessage> sanitized = MessageSanitizer.sanitizeMessages(messages);

        assertEquals(2, sanitized.size());
        assertTrue(sanitized.get(0) instanceof UserMessage);
        assertTrue(sanitized.get(1) instanceof AiMessage);
    }
}
