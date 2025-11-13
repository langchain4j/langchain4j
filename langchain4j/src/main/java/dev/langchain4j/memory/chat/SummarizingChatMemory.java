package dev.langchain4j.memory.chat;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
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
 * when the number of messages exceeds a configurable limit.
 *
 * <p>This class behaves similarly to {@link MessageWindowChatMemory} but adds automatic
 * summarization capabilities through a customizable <b>summary generation function</b>.
 *
 * <p>Key features:
 * <ul>
 *     <li>Supports both static and dynamic configuration of <b>maxMessages</b> —
 *         the maximum number of messages retained before triggering summarization.</li>
 *     <li>Supports both static and dynamic configuration of <b>maxMessagesToSummarize</b> —
 *         the number of messages to remove and summarize once the limit is exceeded.</li>
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
 * <p>Usage notes:
 * <ul>
 *     <li>Messages are summarized only when their count exceeds <b>maxMessages</b>.</li>
 *     <li>The number of messages summarized each time is determined by
 *         <b>maxMessagesToSummarize</b>, and the generated summary is added back into memory.</li>
 *     <li>You can provide your own summary generation function or use the default one,
 *         which only requires a {@link ChatModel} instance.</li>
 *     <li>Dynamic configuration functions allow real-time adjustment of
 *         maxMessages, maxMessagesToSummarize, and system prompts for different memory IDs.</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * SummarizingChatMemory memory = SummarizingChatMemory.builder()
 *     .defaultGenerateSummaryFunction(chatModel) // use default summarization with a ChatModel
 *     .maxMessages(10)
 *     .maxMessagesToSummarize(3)
 *     .build();
 * }</pre>
 *
 * @author PaperFly
 */
public class SummarizingChatMemory implements ChatMemory {

    private final Object id;
    private final ChatMemoryStore store;

    private final Function<Object, Integer> maxMessagesFunction;
    private final Function<Object, Integer> maxMessagesToSummarizeFunction;
    private final Function<List<ChatMessage>, UserMessage> generateSummaryFunction;

    private SummarizingChatMemory(Builder builder) {
        this.id = ensureNotNull(builder.id, "id");
        this.store = ensureNotNull(builder.getStore(), "store");

        this.maxMessagesFunction = ensureNotNull(builder.maxMessagesFunction, "maxMessagesFunction");
        this.maxMessagesToSummarizeFunction =
                ensureNotNull(builder.maxMessagesToSummarizeFunction, "maxMessagesToSummarizeFunction");
        this.generateSummaryFunction = ensureNotNull(builder.generateSummaryFunction, "generateSummaryFunction");

        // Validate configuration once on build
        int max = maxMessagesFunction.apply(id);
        int maxMessagesToSummarize = maxMessagesToSummarizeFunction.apply(id);
        ensureGreaterThanZero(max, "maxMessages");
        ensureGreaterThanZero(maxMessagesToSummarize - 1, "maxMessagesToSummarize -1");
        ensureGreaterThanZero(max - maxMessagesToSummarize, "maxMessages - maxMessagesToSummarize");
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
        checkAndSummarizeMessages(messages);
        store.updateMessages(id, messages);
    }

    @Override
    public List<ChatMessage> messages() {
        List<ChatMessage> messages = new LinkedList<>(store.getMessages(id));
        checkAndSummarizeMessages(messages);
        return messages;
    }

    @Override
    public void clear() {
        store.deleteMessages(id);
    }

    /**
     * Check message list size and trigger summarization if it exceeds maxMessages.
     */
    private void checkAndSummarizeMessages(List<ChatMessage> messages) {
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
     * Builder for {@link SummarizingChatMemory}.
     */
    public static class Builder {
        private static final String DEFAULT_SYSTEM_PROMPT =
                """
                        You are a conversation summarization assistant. Please read the entire history of interactions between the user and the AI, and generate a concise summary.
    
                        Requirements for the summary:
                        - Write in natural and fluent Chinese.
                        - Keep the length between 50 and 500 characters.
                        - Accurately capture the main topics, goals, and conclusions discussed.
                        - Exclude any irrelevant dialogue or system messages.
                        - Maintain a neutral and objective tone, without adding unexpressed or speculative information.
                        - If the conversation covers multiple topics, organize the summary clearly by theme.
                        - Do not include introductions such as “Here is the summary” or “The user said.” Output only the summary text itself.
                        """;
        private Object id = ChatMemoryService.DEFAULT;
        private Function<Object, Integer> maxMessagesFunction;
        private Function<Object, Integer> maxMessagesToSummarizeFunction;
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
         * Sets a dynamic function to determine the maximum number of messages to retain.
         * <p>
         * The function is evaluated to decide how many messages should be kept.
         * If the capacity is exceeded, the oldest messages will be evicted.
         *
         * @param func a function that determines the maximum number of messages to retain
         * @return the builder instance
         */
        public Builder dynamicMaxMessages(Function<Object, Integer> func) {
            this.maxMessagesFunction = func;
            return this;
        }

        /**
         * @param maxMessages The maximum number of messages to retain.
         *                    If there isn't enough space for a new message, the oldest one is evicted.
         * @return builder
         */
        public Builder maxMessages(Integer maxMessages) {
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

        public SummarizingChatMemory build() {
            return new SummarizingChatMemory(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
