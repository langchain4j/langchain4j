package dev.langchain4j.memory.chat;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * The state of chat memory is stored in {@link ChatMemoryStore} ({@link InMemoryChatMemoryStore} is used by default).
 */
public final class TokenWindowChatMemory extends AbstractWindowChatMemory {

    private static final Logger log = LoggerFactory.getLogger(TokenWindowChatMemory.class);

    private final Integer maxTokens;
    private final Tokenizer tokenizer;

    private TokenWindowChatMemory(Builder builder) {
        super(ensureNotNull(builder.id, "id"), ensureNotNull(builder.store, "store"));
        this.maxTokens = ensureGreaterThanZero(builder.maxTokens, "maxTokens");
        this.tokenizer = ensureNotNull(builder.tokenizer, "tokenizer");
    }

    @Override
    protected void ensureCapacity(List<ChatMessage> messages) {

        if (messages.isEmpty()) {
            return;
        }

        int currentTokenCount = tokenizer.estimateTokenCountInMessages(messages);
        while (currentTokenCount > maxTokens) {

            int messageToEvictIndex = 0;
            if (messages.get(0) instanceof SystemMessage) {
                messageToEvictIndex = 1;
            }

            ChatMessage evictedMessage = messages.remove(messageToEvictIndex);
            int tokenCountOfEvictedMessage = tokenizer.estimateTokenCountInMessage(evictedMessage);
            log.trace(
                    "Evicting the following message ({} tokens) to comply with the capacity requirement: {}",
                    tokenCountOfEvictedMessage,
                    evictedMessage);
            currentTokenCount -= tokenCountOfEvictedMessage;

            if (evictedMessage instanceof AiMessage evictedAiMessage && evictedAiMessage.hasToolExecutionRequests()) {
                while (messages.size() > messageToEvictIndex
                        && messages.get(messageToEvictIndex) instanceof ToolExecutionResultMessage) {
                    // Some LLMs (e.g. OpenAI) prohibit ToolExecutionResultMessage(s) without corresponding AiMessage,
                    // so we have to automatically evict orphan ToolExecutionResultMessage(s) if AiMessage was evicted
                    ChatMessage orphanToolExecutionResultMessage = messages.remove(messageToEvictIndex);
                    log.trace("Evicting orphan {}", orphanToolExecutionResultMessage);
                    currentTokenCount -= tokenizer.estimateTokenCountInMessage(orphanToolExecutionResultMessage);
                }
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Object id = "default";
        private Integer maxTokens;
        private Tokenizer tokenizer;
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
         * @param maxTokens The maximum number of tokens to retain.
         *                  Chat memory will retain as many of the most recent messages as can fit into {@code maxTokens}.
         *                  Messages are indivisible. If an old message doesn't fit, it is evicted completely.
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
