package dev.langchain4j.memory.chat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.service.memory.ChatMemoryService;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * This chat memory operates as a sliding window of {@link #maxTokens} tokens.
 * It retains as many of the most recent messages as can fit into the window.
 * If there isn't enough space for a new message, the oldest one (or multiple) is evicted.
 * Messages are indivisible. If a message doesn't fit, it is evicted completely.
 * <p>
 * Once added, a {@link SystemMessage} is always retained.
 * Only one {@code SystemMessage} can be held at a time.
 * If a new {@code SystemMessage} with the same content is added, it is ignored.
 * If a new {@code SystemMessage} with different content is added, the previous {@code SystemMessage} is removed.
 * <p>
 * If an {@link AiMessage} containing {@link ToolExecutionRequest}(s) is evicted,
 * the following orphan {@link ToolExecutionResultMessage}(s) are also automatically evicted
 * to avoid problems with some LLM providers (such as OpenAI)
 * that prohibit sending orphan {@code ToolExecutionResultMessage}(s) in the request.
 * <p>
 * The state of chat memory is stored in {@link ChatMemoryStore} ({@link SingleSlotChatMemoryStore} is used by default).
 */
public class TokenWindowChatMemory implements ChatMemory {

    private final Object id;
    private final Integer maxTokens;
    private final TokenCountEstimator tokenCountEstimator;
    private final ChatMemoryStore store;

    private TokenWindowChatMemory(Builder builder) {
        this.id = ensureNotNull(builder.id, "id");
        this.maxTokens = ensureGreaterThanZero(builder.maxTokens, "maxTokens");
        this.tokenCountEstimator = ensureNotNull(builder.tokenCountEstimator, "tokenCountEstimator");
        this.store = ensureNotNull(builder.store(), "store");
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public void add(ChatMessage message) {
        List<ChatMessage> messages = messages();
        if (message instanceof SystemMessage) {
            Optional<SystemMessage> maybeSystemMessage = findSystemMessage(messages);
            if (maybeSystemMessage.isPresent()) {
                if (maybeSystemMessage.get().equals(message)) {
                    return; // do not add the same system message
                } else {
                    messages.remove(maybeSystemMessage.get()); // need to replace existing system message
                }
            }
        }
        messages.add(message);
        ensureCapacity(messages, maxTokens, tokenCountEstimator);
        store.updateMessages(id, messages);
    }

    private static Optional<SystemMessage> findSystemMessage(List<ChatMessage> messages) {
        return messages.stream()
                .filter(message -> message instanceof SystemMessage)
                .map(message -> (SystemMessage) message)
                .findAny();
    }

    @Override
    public List<ChatMessage> messages() {
        List<ChatMessage> messages = new LinkedList<>(store.getMessages(id));
        ensureCapacity(messages, maxTokens, tokenCountEstimator);
        return messages;
    }

    private static void ensureCapacity(List<ChatMessage> messages, int maxTokens, TokenCountEstimator estimator) {

        if (messages.isEmpty()) {
            return;
        }

        int currentTokenCount = estimator.estimateTokenCountInMessages(messages);
        while (currentTokenCount > maxTokens && !messages.isEmpty()) {

            int messageToEvictIndex = 0;
            if (messages.get(0) instanceof SystemMessage) {
                if (messages.size() == 1) {
                    return;
                }
                messageToEvictIndex = 1;
            }

            ChatMessage evictedMessage = messages.remove(messageToEvictIndex);
            int tokenCountOfEvictedMessage = estimator.estimateTokenCountInMessage(evictedMessage);
            currentTokenCount -= tokenCountOfEvictedMessage;

            if (evictedMessage instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                while (messages.size() > messageToEvictIndex
                        && messages.get(messageToEvictIndex) instanceof ToolExecutionResultMessage) {
                    // Some LLMs (e.g. OpenAI) prohibit ToolExecutionResultMessage(s) without corresponding AiMessage,
                    // so we have to automatically evict orphan ToolExecutionResultMessage(s) if AiMessage was evicted
                    ChatMessage orphanToolExecutionResultMessage = messages.remove(messageToEvictIndex);
                    currentTokenCount -= estimator.estimateTokenCountInMessage(orphanToolExecutionResultMessage);
                }
            }
        }
    }

    @Override
    public void clear() {
        store.deleteMessages(id);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Object id = ChatMemoryService.DEFAULT;
        private Integer maxTokens;
        private TokenCountEstimator tokenCountEstimator;
        private ChatMemoryStore store;

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
         * @param maxTokens The maximum number of tokens to retain.
         *                  Chat memory will retain as many of the most recent messages as can fit into {@code maxTokens}.
         *                  Messages are indivisible. If an old message doesn't fit, it is evicted completely.
         * @param tokenCountEstimator A {@link TokenCountEstimator} responsible for counting tokens in the messages.
         * @return builder
         */
        public Builder maxTokens(Integer maxTokens, TokenCountEstimator tokenCountEstimator) {
            this.maxTokens = maxTokens;
            this.tokenCountEstimator = tokenCountEstimator;
            return this;
        }

        /**
         * @param store The chat memory store responsible for storing the chat memory state.
         *              If not provided, an {@link SingleSlotChatMemoryStore} will be used.
         * @return builder
         */
        public Builder chatMemoryStore(ChatMemoryStore store) {
            this.store = store;
            return this;
        }

        private ChatMemoryStore store() {
            return store != null ? store : new SingleSlotChatMemoryStore(id);
        }

        public TokenWindowChatMemory build() {
            return new TokenWindowChatMemory(this);
        }
    }

    public static TokenWindowChatMemory withMaxTokens(int maxTokens, TokenCountEstimator tokenCountEstimator) {
        return builder().maxTokens(maxTokens, tokenCountEstimator).build();
    }
}
