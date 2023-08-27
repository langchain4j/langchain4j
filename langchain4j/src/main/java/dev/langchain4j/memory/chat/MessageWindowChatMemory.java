package dev.langchain4j.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * This chat memory operates as a sliding window of {@link #maxMessages} messages.
 * It retains as many of the most recent messages as can fit into the window.
 * If there isn't enough space for a new message, the oldest one is discarded.
 * <p>
 * The state of chat memory is stored in {@link ChatMemoryStore}.
 */
public class MessageWindowChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(MessageWindowChatMemory.class);

    private final Object id;
    private final Integer maxMessages;
    private final ChatMemoryStore store;

    private MessageWindowChatMemory(Builder builder) {
        this.id = ensureNotNull(builder.id, "id");
        this.maxMessages = ensureGreaterThanZero(builder.maxMessages, "maxMessages");
        this.store = ensureNotNull(builder.store, "store");
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public void add(ChatMessage message) {
        List<ChatMessage> messages = messages();
        messages.add(message);
        ensureCapacity(messages, maxMessages);
        store.updateMessages(id, messages);
    }

    @Override
    public List<ChatMessage> messages() {
        List<ChatMessage> messages = new ArrayList<>(store.getMessages(id));
        ensureCapacity(messages, maxMessages);
        return messages;
    }

    private static void ensureCapacity(List<ChatMessage> messages, int maxMessages) {
        int currentMessageCount = messages.size();
        while (currentMessageCount > maxMessages) {
            ChatMessage oldestMessage = messages.remove(0);
            log.trace("Removing the oldest message to comply with capacity requirements: {}", oldestMessage);
            currentMessageCount--;
        }
        log.trace("Current message count: {}", currentMessageCount);
    }

    @Override
    public void clear() {
        store.deleteMessages(id);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Object id = "default";
        private Integer maxMessages;
        private ChatMemoryStore store = new InMemoryChatMemoryStore();

        /**
         * @param id The ID of the {@link ChatMemory}.
         *           If not provided, a "default" will be used.
         * @return builder
         */
        public Builder id(Object id) {
            this.id = id;
            return this;
        }

        /**
         * @param maxMessages The maximum number of messages to retain.
         * @return builder
         */
        public Builder maxMessages(Integer maxMessages) {
            this.maxMessages = maxMessages;
            return this;
        }

        /**
         * @param store The chat memory store responsible for storing the chat memory state.
         *              If not provided, an {@link InMemoryChatMemoryStore} will be used.
         * @return builder
         */
        public Builder chatMemoryStore(ChatMemoryStore store) {
            this.store = store;
            return this;
        }

        public MessageWindowChatMemory build() {
            return new MessageWindowChatMemory(this);
        }
    }

    public static MessageWindowChatMemory withMaxMessages(int maxMessages) {
        return builder().maxMessages(maxMessages).build();
    }
}
