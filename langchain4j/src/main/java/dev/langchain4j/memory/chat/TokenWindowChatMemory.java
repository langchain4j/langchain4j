package dev.langchain4j.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * This chat memory operates as a sliding window of {@link #maxTokens} tokens.
 * It retains as many of the most recent messages as can fit into the window.
 * If there isn't enough space for a new message, the oldest one (or multiple) is discarded.
 * Messages are indivisible. If a message doesn't fit, it's discarded completely.
 * <p>
 * The state of chat memory is stored in {@link ChatMemoryStore}.
 */
public class TokenWindowChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(TokenWindowChatMemory.class);

    private final Object userId;
    private final Integer maxTokens;
    private final Tokenizer tokenizer;
    private final ChatMemoryStore store;

    private TokenWindowChatMemory(Builder builder) {
        this.userId = ensureNotNull(builder.userId, "userId");
        this.maxTokens = ensureNotNull(builder.maxTokens, "maxTokens");
        if (this.maxTokens < 1) {
            throw illegalArgument("maxTokens should be greater than 0");
        }
        this.tokenizer = ensureNotNull(builder.tokenizer, "tokenizer");
        this.store = ensureNotNull(builder.store, "store");
    }

    @Override
    public Object userId() {
        return userId;
    }

    @Override
    public void add(ChatMessage message) {
        List<ChatMessage> messages = messages();
        messages.add(message);
        ensureCapacity(messages, maxTokens, tokenizer);
        store.updateMessages(userId, messages);
    }

    @Override
    public List<ChatMessage> messages() {
        List<ChatMessage> messages = new ArrayList<>(store.getMessages(userId));
        ensureCapacity(messages, maxTokens, tokenizer);
        return messages;
    }

    private static void ensureCapacity(List<ChatMessage> messages, int maxTokens, Tokenizer tokenizer) {
        int currentTokenCount = tokenizer.estimateTokenCountInMessages(messages);
        while (currentTokenCount > maxTokens) {
            ChatMessage oldestMessage = messages.remove(0);
            int tokenCountOfOldestMessage = tokenizer.estimateTokenCountInMessage(oldestMessage);
            log.debug("Removing the oldest message ({} tokens) to comply with capacity requirements: {}",
                    tokenCountOfOldestMessage, oldestMessage);
            currentTokenCount -= tokenCountOfOldestMessage;
        }
        log.debug("Current token count: {}", currentTokenCount);
    }

    @Override
    public void clear() {
        store.deleteMessages(userId);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Object userId = randomUUID();
        private Integer maxTokens;
        private Tokenizer tokenizer;
        private ChatMemoryStore store = new InMemoryChatMemoryStore();

        /**
         * @param userId The ID of the user to whom this chat memory belongs.
         *               If not provided, a random UUID will be generated.
         * @return builder
         */
        public Builder userId(Object userId) {
            this.userId = userId;
            return this;
        }

        /**
         * @param maxTokens The maximum number of tokens to retain.
         *                  Chat memory will retain as many of the most recent messages as can fit into {@code maxTokens}.
         *                  Messages are indivisible. If a message doesn't fit, it's discarded completely.
         * @param tokenizer A {@link Tokenizer} responsible for counting tokens in the messages.
         * @return builder
         */
        public Builder maxTokens(Integer maxTokens, Tokenizer tokenizer) {
            this.maxTokens = maxTokens;
            this.tokenizer = tokenizer;
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

        public TokenWindowChatMemory build() {
            return new TokenWindowChatMemory(this);
        }
    }

    public static TokenWindowChatMemory withMaxTokens(int maxTokens, Tokenizer tokenizer) {
        return builder().maxTokens(maxTokens, tokenizer).build();
    }
}
