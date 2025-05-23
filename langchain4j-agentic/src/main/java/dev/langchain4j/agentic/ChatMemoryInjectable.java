package dev.langchain4j.agentic;

import dev.langchain4j.memory.ChatMemory;

public interface ChatMemoryInjectable {
    void setChatMemory(ChatMemory chatMemory);
}
