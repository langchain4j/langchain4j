package dev.langchain4j.memory.chat;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link ChatMemory} that stores state in-memory.
 * <p>
 * This storage mechanism is transient and does not persist data across application restarts.
 */
public class InMemoryChatMemory implements ChatMemory {
    private final ChatMemoryStore store = new InMemoryChatMemoryStore();
    private final Object id;

    public InMemoryChatMemory(Object id) {
        this.id = ensureNotNull(id, "id");
    }

    public InMemoryChatMemory() {
        this(UUID.randomUUID());
    }

    @Override
    public Object id() {
        return this.id;
    }

    @Override
    public void add(ChatMessage message) {
        var messages = messages();

        if (message.type() == ChatMessageType.SYSTEM) {
            findSystemMessage(messages)
                    .filter(message::equals) // Do not add the same system message
                    .ifPresent(messages::remove); // Need to replace the existing system message
        }

        messages.add(message);
        this.store.updateMessages(this.id, messages);
    }

    private static Optional<SystemMessage> findSystemMessage(List<ChatMessage> messages) {
        return messages.stream()
                .filter(SystemMessage.class::isInstance)
                .map(SystemMessage.class::cast)
                .findAny();
    }

    @Override
    public List<ChatMessage> messages() {
        return new LinkedList<>(this.store.getMessages(this.id));
    }

    @Override
    public void clear() {
        this.store.deleteMessages(this.id);
    }
}
