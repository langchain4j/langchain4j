package dev.langchain4j.memory.chat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.Tokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public class TokenWindowChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(TokenWindowChatMemory.class);

    // safety net to limit the cost in case user did not define it himself
    private static final int DEFAULT_CAPACITY_IN_TOKENS = 200;

    private final Tokenizer tokenizer;
    private final Optional<SystemMessage> maybeSystemMessage;
    private final LinkedList<ChatMessage> previousMessages;
    private final Integer capacityInTokens;

    private TokenWindowChatMemory(Builder builder) {
        if (builder.tokenizer == null) {
            throw new IllegalStateException("Tokenizer must be defined");
        }
        this.tokenizer = builder.tokenizer;
        this.maybeSystemMessage = builder.maybeSystemMessage;
        this.previousMessages = builder.previousMessages;
        this.capacityInTokens = builder.capacityInTokens;
        ensureCapacity();
    }

    @Override
    public void add(ChatMessage chatMessage) {
        previousMessages.add(chatMessage);
        ensureCapacity();
    }

    @Override
    public List<ChatMessage> messages() {
        List<ChatMessage> messages = new ArrayList<>();
        maybeSystemMessage.ifPresent(messages::add);
        messages.addAll(previousMessages);
        return messages;
    }

    @Override
    public void clear() {
        previousMessages.clear();
    }

    private void ensureCapacity() {
        int currentNumberOfTokensInHistory = getCurrentTokenCount();

        while (currentNumberOfTokensInHistory > capacityInTokens) {

            ChatMessage oldestMessage = previousMessages.removeFirst();
            int tokenCount = tokenizer.countTokens(oldestMessage);

            log.debug("Removing the oldest {} message '{}' ({} tokens) to comply with capacity requirements",
                    oldestMessage instanceof UserMessage ? "user" : "AI",
                    oldestMessage.text(),
                    tokenCount);

            currentNumberOfTokensInHistory -= tokenCount;
        }

        log.debug("Current token count: {}", getCurrentTokenCount());
    }

    private int getCurrentTokenCount() {
        int systemMessageTokenCount = maybeSystemMessage.map(systemMessage ->
                tokenizer.countTokens(systemMessage)).orElse(0);
        int previousMessagesTokenCount = tokenizer.countTokens(previousMessages);
        return systemMessageTokenCount + previousMessagesTokenCount;
    }

    public static class Builder {

        private Tokenizer tokenizer;
        private Optional<SystemMessage> maybeSystemMessage = Optional.empty();
        private Integer capacityInTokens = DEFAULT_CAPACITY_IN_TOKENS;
        private LinkedList<ChatMessage> previousMessages = new LinkedList<>();

        public Builder tokenizer(Tokenizer tokenizer) {
            this.tokenizer = tokenizer;
            return this;
        }

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

        public Builder capacityInTokens(Integer capacityInTokens) {
            this.capacityInTokens = capacityInTokens;
            return this;
        }

        public Builder previousMessages(List<ChatMessage> previousMessages) {
            if (previousMessages == null) {
                return this;
            }

            this.previousMessages = new LinkedList<>(previousMessages);
            return this;
        }

        public TokenWindowChatMemory build() {
            return new TokenWindowChatMemory(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
