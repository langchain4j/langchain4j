package dev.langchain4j.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.Tokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Chat memory that retains the most recent messages fitting into N tokens.
 * If there isn't enough space for a new message, the oldest one (or multiple ones) is discarded.
 * Optionally, a system message can be set.
 * System message will always be retained at the first position (index 0) and will never be removed.
 */
public class TokenWindowChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(TokenWindowChatMemory.class);

    private final Integer capacity;
    private final Tokenizer tokenizer;
    private final SystemMessage systemMessage;
    private final LinkedList<ChatMessage> messages;

    private TokenWindowChatMemory(Builder builder) {
        this.capacity = ensureNotNull(builder.capacity, "capacity");
        this.tokenizer = ensureNotNull(builder.tokenizer, "tokenizer");
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
        int currentTokenCount = currentTokenCount();

        while (currentTokenCount > capacity) {
            ChatMessage oldestMessage = messages.removeFirst();
            int tokenCountOfOldestMessage = tokenizer.estimateTokenCountInMessage(oldestMessage);
            log.debug("Removing the oldest message ({} tokens) to comply with capacity requirements: {}",
                    tokenCountOfOldestMessage, oldestMessage);
            currentTokenCount -= tokenCountOfOldestMessage;
        }

        log.debug("Current token count: {}", currentTokenCount());
    }

    private int currentTokenCount() {
        return tokenizer.estimateTokenCountInMessages(messages());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Integer capacity;
        private Tokenizer tokenizer;
        private SystemMessage systemMessage;
        private LinkedList<ChatMessage> messages = new LinkedList<>();

        public Builder capacity(Integer capacity, Tokenizer tokenizer) {
            if (capacity < 1) {
                throw illegalArgument("Capacity should be greater than 1");
            }
            this.capacity = capacity;
            this.tokenizer = tokenizer;
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

        public TokenWindowChatMemory build() {
            return new TokenWindowChatMemory(this);
        }
    }

    public static TokenWindowChatMemory withCapacity(int capacity, Tokenizer tokenizer) {
        return builder().capacity(capacity, tokenizer).build();
    }
}
