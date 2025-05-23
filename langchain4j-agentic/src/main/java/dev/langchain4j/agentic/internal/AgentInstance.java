package dev.langchain4j.agentic.internal;

import dev.langchain4j.memory.ChatMemory;

public interface AgentInstance {

    boolean trySetChatMemory(ChatMemory chatMemory);

    String outputName();
}
