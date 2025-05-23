package dev.langchain4j.agentic;

import dev.langchain4j.memory.ChatMemory;

public interface AgentInstance {

    void setChatMemory(ChatMemory chatMemory);

    String outputName();
}
