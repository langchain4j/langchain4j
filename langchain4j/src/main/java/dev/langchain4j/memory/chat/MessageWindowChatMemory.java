package dev.langchain4j.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import lombok.val;
import lombok.var;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class MessageWindowChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(MessageWindowChatMemory.class);

    private final Optional<SystemMessage> maybeSystemMessage;
    private final LinkedList<ChatMessage> previousMessages;
    private final Integer capacity;

    private MessageWindowChatMemory(Builder builder) {
        this.maybeSystemMessage = builder.maybeSystemMessage;
        this.previousMessages = builder.previousMessages;
        this.capacity = builder.capacity;
        ensureCapacity();
    }

    @Override
    public void add(ChatMessage message) {
        previousMessages.add(message);
        ensureCapacity();
    }

    @Override
    public List<ChatMessage> messages() {
        val messages = new ArrayList<ChatMessage>();
        maybeSystemMessage.ifPresent(messages::add);
        messages.addAll(previousMessages);
        return messages;
    }

    @Override
    public void clear() {
        previousMessages.clear();
    }

    private void ensureCapacity() {
        var currentNumberOfMessagesInHistory = getCurrentNumberOfMessages();

        while (currentNumberOfMessagesInHistory > capacity) {

            ChatMessage oldestMessage = previousMessages.removeFirst();

            log.debug("Removing the oldest message from {} '{}' to comply with capacity requirements",
                    oldestMessage instanceof UserMessage ? "user" : "AI",
                    oldestMessage.text());

            currentNumberOfMessagesInHistory--;
        }

        log.debug("Current message count: {}", getCurrentNumberOfMessages());
    }

    private int getCurrentNumberOfMessages() {
        return maybeSystemMessage.map(m -> 1).orElse(0) + previousMessages.size();
    }

    public static class Builder {

        private Optional<SystemMessage> maybeSystemMessage = Optional.empty();
        private Integer capacity;
        private LinkedList<ChatMessage> previousMessages = new LinkedList<>();

        public Builder systemMessage(SystemMessage systemMessage) {
            this.maybeSystemMessage = Optional.ofNullable(systemMessage);
            return this;
        }

        public Builder systemMessage(String systemMessage) {
            if (systemMessage == null) {
                this.maybeSystemMessage = Optional.empty();
                return this;
            }

            return systemMessage(SystemMessage.from(systemMessage));
        }

        public Builder capacityInMessages(Integer capacityInMessages) {
            this.capacity = capacityInMessages;
            return this;
        }

        public Builder previousMessages(List<ChatMessage> previousMessages) {
            if (previousMessages == null) {
                return this;
            }

            this.previousMessages = new LinkedList<>(previousMessages);
            return this;
        }

        public MessageWindowChatMemory build() {
            return new MessageWindowChatMemory(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static MessageWindowChatMemory withCapacity(int n) {
        return builder().capacityInMessages(n).build();
    }
}
