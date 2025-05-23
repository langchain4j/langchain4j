package dev.langchain4j.agentic;

import dev.langchain4j.memory.ChatMemory;

public interface ChatState extends ChatMemory {

    void writeState(String state, Object value);
    boolean hasState(String state);
    Object readState(String state);
}
