package dev.langchain4j.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

/**
 * This abstract chat memory serves as a foundation for implementing window-based chat memories.
 * It provides the core functionality needed to manage message retention within a defined window.
 * <p>
 * Currently, two implementations are available:
 * <ul>
 *   <li>{@link MessageWindowChatMemory} – maintains a fixed number of recent messages.</li>
 *   <li>{@link TokenWindowChatMemory} – retains messages within a specified token limit.</li>
 * </ul>
 */
public abstract sealed class AbstractWindowChatMemory implements ChatMemory
        permits MessageWindowChatMemory, TokenWindowChatMemory {

    protected final Object id;
    protected final ChatMemoryStore store;

    protected AbstractWindowChatMemory(final Object id, final ChatMemoryStore store) {
        this.id = id;
        this.store = store;
    }

    @Override
    public void add(ChatMessage message) {
        List<ChatMessage> messages = messages();
        // do not add the same system message
        if (addChatMessage(message, messages)) {
            store.updateMessages(id, messages);
        }
    }

    @Override
    public void addAll(final List<ChatMessage> messages) {
        List<ChatMessage> currentMessages = messages();
        boolean updated = messages.stream()
                .map(message -> addChatMessage(message, currentMessages))
                .reduce(false, (a, b) -> a || b);
        if (updated) {
            store.updateMessages(id, currentMessages);
        }
    }

    protected boolean addChatMessage(ChatMessage message, List<ChatMessage> messages) {
        if (message instanceof SystemMessage) {
            Optional<SystemMessage> systemMessage = findSystemMessage(messages);
            if (systemMessage.isPresent()) {
                if (systemMessage.get().equals(message)) {
                    return false;
                } else {
                    messages.remove(systemMessage.get()); // need to replace existing system message
                }
            }
        }
        messages.add(message);
        ensureCapacity(messages);
        return true;
    }

    @Override
    public void clear() {
        store.deleteMessages(id);
    }

    protected abstract void ensureCapacity(List<ChatMessage> messages);

    private static Optional<SystemMessage> findSystemMessage(List<ChatMessage> messages) {
        return messages.stream()
                .filter(SystemMessage.class::isInstance)
                .map(SystemMessage.class::cast)
                .findAny();
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public List<ChatMessage> messages() {
        List<ChatMessage> messages = new LinkedList<>(store.getMessages(id));
        ensureCapacity(messages);
        return messages;
    }
}
