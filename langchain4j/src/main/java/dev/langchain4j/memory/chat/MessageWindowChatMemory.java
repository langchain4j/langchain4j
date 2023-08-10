package dev.langchain4j.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * This chat memory operates as a sliding window of {@link #maxMessages} messages.
 * It retains as many of the most recent messages as can fit into the window.
 * If there isn't enough space for a new message, the oldest one is discarded.
 * Optionally, a system message can be set.
 * System message will always be retained at the first position (index 0) and will never be removed.
 */
public class MessageWindowChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(MessageWindowChatMemory.class);

    private final Integer maxMessages;
    private final SystemMessage systemMessage;
    private final LinkedList<ChatMessage> messages;

    private MessageWindowChatMemory(Builder builder) {
        this.maxMessages = ensureNotNull(builder.maxMessages, "maxMessages");
        if (this.maxMessages < 1) {
            throw illegalArgument("maxMessages should be greater than 0");
        }
        this.systemMessage = builder.systemMessage;
        this.messages = ensureNotNull(builder.messages, "messages");
        ensureCapacity();
    }

    @Override
    public void add(ChatMessage message) {
        messages.add(message);
        ensureCapacity();
    }

    @Override
    public List<ChatMessage> messages() {
        List<ChatMessage> messages = new ArrayList<>();
        if (systemMessage != null) {
            messages.add(systemMessage);
        }
        messages.addAll(this.messages);
        return messages;
    }

    @Override
    public void clear() {
        messages.clear();
    }

    private void ensureCapacity() {
        int currentMessageCount = currentMessageCount();

        while (currentMessageCount > maxMessages) {
            ChatMessage oldestMessage = messages.removeFirst();
            log.debug("Removing the oldest message to comply with capacity requirements: {}", oldestMessage);
            currentMessageCount--;
        }

        log.debug("Current message count: {}", currentMessageCount());
    }

    private int currentMessageCount() {
        return messages().size();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Integer maxMessages;
        private SystemMessage systemMessage;
        private LinkedList<ChatMessage> messages = new LinkedList<>();

        public Builder maxMessages(Integer maxMessages) {
            this.maxMessages = maxMessages;
            return this;
        }

        public Builder systemMessage(String systemMessage) {
            return systemMessage(SystemMessage.from(systemMessage));
        }

        public Builder systemMessage(SystemMessage systemMessage) {
            this.systemMessage = systemMessage;
            return this;
        }

        public Builder messages(List<ChatMessage> messages) {
            if (messages == null) {
                return this;
            }

            this.messages = new LinkedList<>(messages);
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
