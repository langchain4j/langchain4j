package dev.langchain4j.memory.chat;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

public interface ChatMemoryBuilder {
    ChatMemoryBuilder chatMemoryStore(ChatMemoryStore store);

    ChatMemory build();
}
