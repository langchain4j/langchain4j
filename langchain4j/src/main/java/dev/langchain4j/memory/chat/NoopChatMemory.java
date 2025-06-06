package dev.langchain4j.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import java.util.List;

/**
 * An implementation of {@link ChatMemory} that does nothing.
 * This is useful for simplifying the AiService code.
 */
public class NoopChatMemory implements ChatMemory {
    @Override
    public Object id() {
        return "default";
    }

    @Override
    public void add(final ChatMessage message) {}

    @Override
    public List<ChatMessage> messages() {
        return List.of();
    }

    @Override
    public void clear() {}
}
