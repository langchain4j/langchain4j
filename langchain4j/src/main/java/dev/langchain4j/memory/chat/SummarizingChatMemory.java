package dev.langchain4j.memory.chat;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.memory.ChatMemoryService;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A chat memory implementation that automatically summarizes conversation history
 * when a configurable limit is exceeded.
 *
 * <p>This class supports two mutually exclusive triggering modes:
 * <ul>
 *     <li><b>Message-count mode</b>: Summarization is triggered when the number of messages
 *         exceeds {@code maxMessages}. This is the original behavior.</li>
 *     <li><b>Token-based mode</b>: Summarization is triggered when the total token count
 *         exceeds {@code maxTokens}. This requires a {@link TokenCountEstimator}.</li>
 * </ul>
 *
 * <p>This class behaves similarly to {@link MessageWindowChatMemory} but adds automatic
 * summarization capabilities through a customizable <b>summary generation function</b>.
 *
 * <p>Key features:
 * <ul>
 *     <li>Supports both static and dynamic configuration of <b>maxMessages</b> or <b>maxTokens</b> —
 *         the threshold before triggering summarization.</li>
 *     <li>Supports both static and dynamic configuration of <b>maxMessagesToSummarize</b> or
 *         <b>maxTokensToSummarize</b> — the amount to remove and summarize once the limit is exceeded.</li>
 *     <li>Supports dynamic generation of <b>system prompts</b> per memory ID,
 *         allowing context-specific summarization instructions.</li>
 *     <li>Automatically evicts orphaned {@link ToolExecutionResultMessage}s
 *         when their parent {@link AiMessage} is evicted.</li>
 *     <li>Ensures that {@link SystemMessage}s are preserved at the beginning of the memory
 *         and are never summarized or removed.</li>
 *     <li>Integrates with any {@link ChatMemoryStore} to persist memory state.</li>
 *     <li>Allows injection of a custom summary generation function, or uses the
 *         <b>default summary function</b> </li>
 * </ul>
 *
 * <p>Example usage (message-count mode):
 * <pre>{@code
 * SummarizingChatMemory memory = SummarizingChatMemory.builder()
 *     .defaultGenerateSummaryFunction(chatModel)
 *     .maxMessages(10)
 *     .maxMessagesToSummarize(3)
 *     .build();
 * }</pre>
 *
 * <p>Example usage (token-based mode):
 * <pre>{@code
 * SummarizingChatMemory memory = SummarizingChatMemory.builder()
 *     .defaultGenerateSummaryFunction(chatModel)
 *     .maxTokens(4000, tokenCountEstimator)
 *     .build();
 * }</pre>
 *
 * @author PaperFly
 */
public class SummarizingChatMemory implements ChatMemory {

    /**
     * Defines the triggering mode for summarization.
     */
    private enum TriggerMode {
        MESSAGE_COUNT,
        TOKEN_BASED
    }

    private final Object id;
    private final ChatMemoryStore store;
    private final TriggerMode triggerMode;

    // Message-count mode fields
    private final Function<Object, Integer> maxMessagesFunction;
    private final Function<Object, Integer> maxMessagesToSummarizeFunction;

    // Token-based mode fields
    private final Function<Object, Integer> maxTokensFunction;
    private final Function<Object, Integer> maxTokensToSummarizeFunction;
    private final TokenCountEstimator tokenCountEstimator;

    private final Function<List<ChatMessage>, UserMessage> generateSummaryFunction;

    private SummarizingChatMemory(Builder builder) {
        this.id = ensureNotNull(builder.id, "id");
        this.store = ensureNotNull(builder.getStore(), "store");
        this.generateSummaryFunction = ensureNotNull(builder.generateSummaryFunction, "generateSummaryFunction");

        // Determine trigger mode
        this.triggerMode = builder.determineTriggerMode();

        if (triggerMode == TriggerMode.MESSAGE_COUNT) {
            this.maxMessagesFunction = ensureNotNull(builder.maxMessagesFunction, "maxMessagesFunction");
            this.maxMessagesToSummarizeFunction =
                    ensureNotNull(builder.maxMessagesToSummarizeFunction, "maxMessagesToSummarizeFunction");
            this.maxTokensFunction = null;
            this.maxTokensToSummarizeFunction = null;
            this.tokenCountEstimator = null;

            // Validate message-count configuration once on build
            int max = maxMessagesFunction.apply(id);
            int maxMessagesToSummarize = maxMessagesToSummarizeFunction.apply(id);
            ensureGreaterThanZero(max, "maxMessages");
            ensureGreaterThanZero(maxMessagesToSummarize - 1, "maxMessagesToSummarize -1");
            ensureGreaterThanZero(max - maxMessagesToSummarize, "maxMessages - maxMessagesToSummarize");
        } else {
            this.maxTokensFunction = ensureNotNull(builder.maxTokensFunction, "maxTokensFunction");
            this.tokenCountEstimator = ensureNotNull(builder.tokenCountEstimator, "tokenCountEstimator");
            this.maxTokensToSummarizeFunction = builder.maxTokensToSummarizeFunction;
            this.maxMessagesFunction = null;
            this.maxMessagesToSummarizeFunction = null;

            // Validate token-based configuration once on build
            int maxTokens = maxTokensFunction.apply(id);
            ensureGreaterThanZero(maxTokens, "maxTokens");
        }
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public void add(ChatMessage message) {
        List<ChatMessage> messages = messages();

        // Replace or ignore duplicate system messages
        if (message instanceof SystemMessage) {
            Optional<SystemMessage> systemMessage = SystemMessage.findFirst(messages);
            if (systemMessage.isPresent()) {
                if (systemMessage.get().equals(message)) {
                    return; // Skip identical system messages
                } else {
                    messages.remove(systemMessage.get());
                }
            }
        }

        messages.add(message);
        if (triggerMode == TriggerMode.MESSAGE_COUNT) {
            checkAndSummarizeMessagesByCount(messages);
        } else {
            checkAndSummarizeMessagesByTokens(messages);
        }
        store.updateMessages(id, messages);
    }

    @Override
    public List<ChatMessage> messages() {
        List<ChatMessage> messages = new LinkedList<>(store.getMessages(id));
        if (triggerMode == TriggerMode.MESSAGE_COUNT) {
            checkAndSummarizeMessagesByCount(messages);
        } else {
            checkAndSummarizeMessagesByTokens(messages);
        }
        return messages;
    }

    @Override
    public void clear() {
        store.deleteMessages(id);
    }

    /**
     * Check message list size and trigger summarization if it exceeds maxMessages (message-count mode).
     */
    private void checkAndSummarizeMessagesByCount(List<ChatMessage> messages) {
        int maxMessages = maxMessagesFunction.apply(id);
        int maxMessagesToSummarize = maxMessagesToSummarizeFunction.apply(id);

        ensureGreaterThanZero(maxMessages, "maxMessages");
        ensureGreaterThanZero(maxMessagesToSummarize - 1, "maxMessagesToSummarize -1");
        ensureGreaterThanZero(maxMessages - maxMessagesToSummarize, "maxMessages - maxMessagesToSummarize");

        if (messages.size() <= maxMessages) return;

        List<ChatMessage> removedMessages = new ArrayList<>();
        int removedCount = 0;
        while (!messages.isEmpty() && removedCount < maxMessagesToSummarize) {
            int evictIndex = (messages.get(0) instanceof SystemMessage) ? 1 : 0;

            ChatMessage evicted = messages.remove(evictIndex);
            removedMessages.add(evicted);
            removedCount++;
            // Remove tool execution results associated with the evicted AI message
            if (evicted instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                while (messages.size() > evictIndex && messages.get(evictIndex) instanceof ToolExecutionResultMessage) {
                    removedMessages.add(messages.remove(evictIndex));
                }
            }
        }
        int insertIndex = (!messages.isEmpty() && messages.get(0) instanceof SystemMessage) ? 1 : 0;
        messages.add(insertIndex, this.generateSummaryFunction.apply(removedMessages));
    }

    /**
     * Check message list token count and trigger summarization if it exceeds maxTokens (token-based mode).
     */
    private void checkAndSummarizeMessagesByTokens(List<ChatMessage> messages) {
        int maxTokens = maxTokensFunction.apply(id);
        ensureGreaterThanZero(maxTokens, "maxTokens");

        if (messages.isEmpty()) {
            return;
        }

        int currentTokenCount = tokenCountEstimator.estimateTokenCountInMessages(messages);
        if (currentTokenCount <= maxTokens) {
            return;
        }

        // Determine how many tokens worth of messages to summarize
        int targetTokensToSummarize;
        if (maxTokensToSummarizeFunction != null) {
            targetTokensToSummarize = maxTokensToSummarizeFunction.apply(id);
        } else {
            // Default: summarize enough to get back under the limit with some buffer
            targetTokensToSummarize = currentTokenCount - (maxTokens / 2);
        }

        List<ChatMessage> removedMessages = new ArrayList<>();
        int removedTokenCount = 0;

        while (!messages.isEmpty() && removedTokenCount < targetTokensToSummarize) {
            int evictIndex = (messages.get(0) instanceof SystemMessage) ? 1 : 0;

            // Don't evict if only system message remains
            if (evictIndex >= messages.size()) {
                break;
            }

            ChatMessage evicted = messages.remove(evictIndex);
            removedMessages.add(evicted);
            removedTokenCount += tokenCountEstimator.estimateTokenCountInMessage(evicted);

            // Remove tool execution results associated with the evicted AI message
            if (evicted instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                while (messages.size() > evictIndex && messages.get(evictIndex) instanceof ToolExecutionResultMessage) {
                    ChatMessage orphan = messages.remove(evictIndex);
                    removedMessages.add(orphan);
                    removedTokenCount += tokenCountEstimator.estimateTokenCountInMessage(orphan);
                }
            }
        }

        if (!removedMessages.isEmpty()) {
            int insertIndex = (!messages.isEmpty() && messages.get(0) instanceof SystemMessage) ? 1 : 0;
            messages.add(insertIndex, this.generateSummaryFunction.apply(removedMessages));
        }

        // Recursively check if we're still over limit after adding summary
        currentTokenCount = tokenCountEstimator.estimateTokenCountInMessages(messages);
        if (currentTokenCount > maxTokens && messages.size() > 1) {
            checkAndSummarizeMessagesByTokens(messages);
        }
    }

    /**
     * Builder for {@link SummarizingChatMemory}.
     */
    public static class Builder {
        private static final String DEFAULT_SYSTEM_PROMPT =
                """
                        You are a conversation summarization assistant. Please read the entire history of interactions between the user and the AI, and generate a concise summary.

                        Requirements for the summary:
                        - Keep the length between 50 and 500 characters.
                        - Accurately capture the main topics, goals, and conclusions discussed.
                        - Exclude any irrelevant dialogue or system messages.
                        - Maintain a neutral and objective tone, without adding unexpressed or speculative information.
                        - If the conversation covers multiple topics, organize the summary clearly by theme.
                        - Do not include introductions such as "Here is the summary" or "The user said." Output only the summary text itself.
                        """;
        private Object id = ChatMemoryService.DEFAULT;

        // Message-count mode fields
        private Function<Object, Integer> maxMessagesFunction;
        private Function<Object, Integer> maxMessagesToSummarizeFunction;

        // Token-based mode fields
        private Function<Object, Integer> maxTokensFunction;
        private Function<Object, Integer> maxTokensToSummarizeFunction;
        private TokenCountEstimator tokenCountEstimator;

        private Function<List<ChatMessage>, UserMessage> generateSummaryFunction;
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
         * Sets a dynamic function to determine the maximum number of messages to retain (message-count mode).
         * <p>
         * The function is evaluated to decide how many messages should be kept.
         * If the capacity is exceeded, the oldest messages will be evicted and summarized.
         * <p>
         * This is mutually exclusive with token-based mode configuration ({@link #maxTokens}).
         *
         * @param func a function that determines the maximum number of messages to retain
         * @return the builder instance
         * @throws IllegalStateException if token-based mode is already configured
         */
        public Builder dynamicMaxMessages(Function<Object, Integer> func) {
            ensureTokenModeNotConfigured();
            this.maxMessagesFunction = func;
            return this;
        }

        /**
         * Sets the maximum number of messages to retain before triggering summarization (message-count mode).
         * <p>
         * If there isn't enough space for a new message, the oldest messages are evicted and summarized.
         * <p>
         * This is mutually exclusive with token-based mode configuration ({@link #maxTokens}).
         *
         * @param maxMessages The maximum number of messages to retain.
         * @return builder
         * @throws IllegalStateException if token-based mode is already configured
         */
        public Builder maxMessages(Integer maxMessages) {
            ensureTokenModeNotConfigured();
            this.maxMessagesFunction = (id) -> maxMessages;
            return this;
        }

        /**
         * Sets a dynamic function to determine how many messages should be summarized
         * when the total message count exceeds the configured {@code maxMessages}.
         *
         * <p>When the number of messages exceeds {@code maxMessages},
         * the system will remove the specified number of messages
         * (determined by this function’s return value), generate a summary
         * of those messages, and insert the summary back into the memory.
         *
         * @param func a function that returns the number of messages to remove and summarize
         *             when the total exceeds {@code maxMessages}. Must return a value greater than 1.
         * @return the builder instance
         */
        public Builder dynamicMaxMessagesToSummarize(Function<Object, Integer> func) {
            this.maxMessagesToSummarizeFunction = func;
            return this;
        }

        /**
         * Sets a static number of messages to remove and summarize
         * when the total message count exceeds the configured {@code maxMessages}.
         *
         * <p>When the number of messages exceeds {@code maxMessages},
         * the system will remove the specified number of messages
         * (defined by {@code maxMessagesToSummarize}), generate a summary
         * of those messages, and insert the summary back into the memory.
         *
         * @param maxMessagesToSummarize the fixed number of messages to remove and summarize
         *                               when total messages exceed {@code maxMessages}.
         *                               Must be greater than 1.
         * @return the builder instance
         */
        public Builder maxMessagesToSummarize(Integer maxMessagesToSummarize) {
            this.maxMessagesToSummarizeFunction = (id) -> maxMessagesToSummarize;
            return this;
        }

        // ============ TOKEN-BASED MODE CONFIGURATION ============

        /**
         * Sets the maximum number of tokens to retain before triggering summarization (token-based mode).
         * <p>
         * This is mutually exclusive with message-count mode configuration ({@link #maxMessages}).
         * A {@link TokenCountEstimator} must also be configured via {@link #tokenCountEstimator}
         * or by using {@link #maxTokens(Integer, TokenCountEstimator)}.
         *
         * @param maxTokens the maximum number of tokens before summarization is triggered
         * @return the builder instance
         * @throws IllegalStateException if message-count mode is already configured
         */
        public Builder maxTokens(Integer maxTokens) {
            ensureMessageModeNotConfigured();
            this.maxTokensFunction = (id) -> maxTokens;
            return this;
        }

        /**
         * Sets the maximum number of tokens and the token count estimator (token-based mode).
         * <p>
         * This is a convenience method combining {@link #maxTokens(Integer)} and
         * {@link #tokenCountEstimator(TokenCountEstimator)}.
         *
         * @param maxTokens the maximum number of tokens before summarization is triggered
         * @param tokenCountEstimator the estimator used to count tokens in messages
         * @return the builder instance
         * @throws IllegalStateException if message-count mode is already configured
         */
        public Builder maxTokens(Integer maxTokens, TokenCountEstimator tokenCountEstimator) {
            return maxTokens(maxTokens).tokenCountEstimator(tokenCountEstimator);
        }

        /**
         * Sets a dynamic function to determine the maximum number of tokens to retain (token-based mode).
         * <p>
         * This is mutually exclusive with message-count mode configuration.
         *
         * @param func a function that determines the maximum number of tokens to retain
         * @return the builder instance
         * @throws IllegalStateException if message-count mode is already configured
         */
        public Builder dynamicMaxTokens(Function<Object, Integer> func) {
            ensureMessageModeNotConfigured();
            this.maxTokensFunction = func;
            return this;
        }

        /**
         * Sets the token count estimator used for token-based mode.
         * <p>
         * This is required when using token-based mode.
         *
         * @param tokenCountEstimator the estimator used to count tokens in messages
         * @return the builder instance
         */
        public Builder tokenCountEstimator(TokenCountEstimator tokenCountEstimator) {
            this.tokenCountEstimator = tokenCountEstimator;
            return this;
        }

        /**
         * Sets a static number of tokens worth of messages to remove and summarize
         * when the total token count exceeds the configured {@code maxTokens}.
         * <p>
         * If not set, a default strategy will be used that aims to reduce the token
         * count to half of {@code maxTokens}.
         *
         * @param maxTokensToSummarize the target number of tokens worth of messages to summarize
         * @return the builder instance
         */
        public Builder maxTokensToSummarize(Integer maxTokensToSummarize) {
            this.maxTokensToSummarizeFunction = (id) -> maxTokensToSummarize;
            return this;
        }

        /**
         * Sets a dynamic function to determine how many tokens worth of messages to summarize.
         *
         * @param func a function that returns the number of tokens worth of messages to summarize
         * @return the builder instance
         */
        public Builder dynamicMaxTokensToSummarize(Function<Object, Integer> func) {
            this.maxTokensToSummarizeFunction = func;
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

        /**
         * Sets a custom conversation summarization function.
         * <p>
         * When the total number of tokens in the chat history exceeds {@code maxTokens},
         * this function is used to generate a condensed summary of past messages.
         * The summary helps preserve conversational context while reducing token usage.
         * </p>
         *
         * @param generateSummaryFunction a custom summary generation function that takes
         *                                a list of {@link ChatMessage} instances and
         *                                returns a {@link UserMessage} containing the summary
         * @return this {@code Builder} instance
         */
        public Builder generateSummaryFunction(Function<List<ChatMessage>, UserMessage> generateSummaryFunction) {
            this.generateSummaryFunction = generateSummaryFunction;
            return this;
        }

        /**
         * Initializes a default summary generation function.
         * <p>
         * The default implementation prepends a system prompt ({@link #DEFAULT_SYSTEM_PROMPT})
         * and uses the provided {@link ChatModel} to generate a short summary of the
         * conversation history. This method is useful when no custom summary function is specified.
         * </p>
         *
         * @param chatModel the {@link ChatModel} used to generate conversation summaries
         * @return this {@code Builder} instance
         */
        public Builder defaultGenerateSummaryFunction(ChatModel chatModel) {
            this.generateSummaryFunction = (messages -> {
                SystemMessage systemPrompt = SystemMessage.from(DEFAULT_SYSTEM_PROMPT);

                List<ChatMessage> chatMessages = new ArrayList<>();
                chatMessages.add(systemPrompt);
                chatMessages.addAll(messages);
                ChatResponse chatResponse = chatModel.chat(chatMessages);

                return UserMessage.userMessage(chatResponse.aiMessage().text());
            });
            return this;
        }

        private ChatMemoryStore getStore() {
            return store != null ? store : new SingleSlotChatMemoryStore(id);
        }

        private boolean isMessageModeConfigured() {
            return maxMessagesFunction != null;
        }

        private boolean isTokenModeConfigured() {
            return maxTokensFunction != null;
        }

        private void ensureMessageModeNotConfigured() {
            if (isMessageModeConfigured()) {
                throw new IllegalStateException(
                        "Cannot configure token-based mode when message-count mode (maxMessages) is already set. "
                                + "These modes are mutually exclusive.");
            }
        }

        private void ensureTokenModeNotConfigured() {
            if (isTokenModeConfigured()) {
                throw new IllegalStateException(
                        "Cannot configure message-count mode when token-based mode (maxTokens) is already set. "
                                + "These modes are mutually exclusive.");
            }
        }

        TriggerMode determineTriggerMode() {
            boolean messageMode = isMessageModeConfigured();
            boolean tokenMode = isTokenModeConfigured();

            if (!messageMode && !tokenMode) {
                throw new IllegalStateException(
                        "Either message-count mode (maxMessages) or token-based mode (maxTokens) must be configured.");
            }

            if (messageMode && tokenMode) {
                throw new IllegalStateException(
                        "Cannot configure both message-count mode (maxMessages) and token-based mode (maxTokens). "
                                + "These modes are mutually exclusive.");
            }

            if (tokenMode && tokenCountEstimator == null) {
                throw new IllegalStateException("Token-based mode requires a TokenCountEstimator. "
                        + "Use tokenCountEstimator() or maxTokens(Integer, TokenCountEstimator).");
            }

            return tokenMode ? TriggerMode.TOKEN_BASED : TriggerMode.MESSAGE_COUNT;
        }

        public SummarizingChatMemory build() {
            ensureNotNull(generateSummaryFunction, "generateSummaryFunction");
            determineTriggerMode(); // Validates configuration
            return new SummarizingChatMemory(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
