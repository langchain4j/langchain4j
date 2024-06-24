package dev.langchain4j.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EphemeralChatMemory implements ChatMemory {

    private List<ChatMessage> messages;
    private final UUID id;

    public EphemeralChatMemory() {
        this.id = UUID.randomUUID();
    }
    @Override
    public Object id() {
        return id;
    }

    @Override
    public void add(ChatMessage message) {
        if (messages == null) {
            messages = new ArrayList<>();
        }
        messages.add(message);
    }

    @Override
    public List<ChatMessage> messages() {
        return messages;
    }

    @Override
    public void clear() {
        messages.clear();
    }
}
