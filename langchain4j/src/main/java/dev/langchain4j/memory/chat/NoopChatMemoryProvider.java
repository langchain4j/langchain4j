package dev.langchain4j.memory.chat;

import dev.langchain4j.memory.ChatMemory;

/**
 * An implementation of {@link ChatMemoryProvider} that does nothing.
 * This is useful for simplifying the AiService code.
 */
public class NoopChatMemoryProvider implements ChatMemoryProvider {
    @Override
    public ChatMemory get(Object memoryId) {
        return new NoopChatMemory();
    }
}
