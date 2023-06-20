package dev.langchain4j.memory;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

public interface ChatMemory {

    void add(ChatMessage chatMessage);

    List<ChatMessage> messages();

    void clear();
}
