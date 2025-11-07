package dev.langchain4j.memory.chat;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
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
 * when the number of messages exceeds a configurable threshold.
 *
 * <p>This class behaves similarly to {@link MessageWindowChatMemory} but adds automatic
 * summarization capabilities using an underlying {@link ChatModel}.
 *
 * <p>Key features:
 * <ul>
 *     <li>Supports both static and dynamic configuration of <b>maxMessages</b>:
 *         the maximum number of messages retained before triggering evictions.</li>
 *     <li>Supports both static and dynamic configuration of <b>summarizeThreshold</b>:
 *         the number of messages to remove and summarize once the message limit is exceeded.
 *         Must be greater than 1.</li>
 *     <li>Supports dynamic generation of <b>system prompts</b> per memory ID,
 *         allowing context-specific instructions for summarization.</li>
 *     <li>Automatically evicts orphaned {@link ToolExecutionResultMessage}s
 *         when their parent {@link AiMessage} is evicted.</li>
 *     <li>Ensures that {@link SystemMessage}s are preserved at the head of the memory
 *         and never summarized or evicted.</li>
 *     <li>Integrates with any {@link ChatMemoryStore} to persist memory state.</li>
 * </ul>
 *
 * <p>Usage notes:
 * <ul>
 *     <li>Messages are only summarized when the total message count exceeds <b>maxMessages</b>.</li>
 *     <li>The number of messages removed during summarization is determined by
 *         <b>summarizeThreshold</b>, and the summary is inserted back into the memory.</li>
 *     <li>Dynamic configuration functions allow real-time adjustment of
 *         maxMessages, summarizeThreshold, and system prompts for different memory IDs.</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * SummarizingChatMemory memory = SummarizingChatMemory.builder()
 *     .chatModel(chatModel)
 *     .maxMessages(10)
 *     .summarizeThreshold(3)
 *     .dynamicSystemPrompt(id -> "Summarize conversation for user: " + id)
 *     .build();
 * }</pre>
 *
 * @author PaperFly
 */
public class SummarizingChatMemory implements ChatMemory {

    private final Object id;
    private final ChatMemoryStore store;
    private final ChatModel chatModel;

    private final Function<Object, Integer> maxMessagesFunction;
    private final Function<Object, Integer> summarizeThresholdFunction;
    private final Function<Object, String> systemPromptFunction;

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

    private SummarizingChatMemory(Builder builder) {
        this.id = ensureNotNull(builder.id, "id");
        this.store = ensureNotNull(builder.getStore(), "store");
        this.chatModel = ensureNotNull(builder.chatModel, "chatModel");

        this.systemPromptFunction = ensureNotNull(builder.getSystemPromptFunction(), "systemPromptFunction");

        this.maxMessagesFunction = ensureNotNull(builder.maxMessagesFunction, "maxMessagesFunction");
        this.summarizeThresholdFunction =
                ensureNotNull(builder.summarizeThresholdFunction, "summarizeThresholdFunction");

        // Validate configuration once on build
        int max = maxMessagesFunction.apply(id);
        int threshold = summarizeThresholdFunction.apply(id);
        ensureGreaterThanZero(max, "maxMessages");
        ensureGreaterThanZero(threshold - 1, "summarizeThreshold -1");
        ensureGreaterThanZero(max - threshold, "maxMessages - summarizeThreshold");
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
     * Check message list size and trigger summarization if it exceeds threshold.
     */
    private void checkAndSummarizeMessages(List<ChatMessage> messages) {
        int maxMessages = maxMessagesFunction.apply(id);
        int summarizeThreshold = summarizeThresholdFunction.apply(id);

        ensureGreaterThanZero(maxMessages, "maxMessages");
        ensureGreaterThanZero(summarizeThreshold - 1, "summarizeThreshold -1");
        ensureGreaterThanZero(maxMessages - summarizeThreshold, "maxMessages - summarizeThreshold");

        if (messages.size() <= maxMessages) return;

        List<ChatMessage> removedMessages = new ArrayList<>();
        int removedCount = 0;
        while (!messages.isEmpty() && removedCount < summarizeThreshold) {
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
        messages.add(insertIndex, generateSummary(removedMessages));
    }

    /**
     * Generate a concise AI summary for the removed messages.
     */
    private AiMessage generateSummary(List<ChatMessage> messages) {
        SystemMessage systemPrompt = SystemMessage.from(systemPromptFunction.apply(id));
        messages.add(0, systemPrompt);
        ChatResponse chatResponse = chatModel.chat(messages);
        return AiMessage.aiMessage(chatResponse.aiMessage().text());
    }

    /**
     * Builder for {@link SummarizingChatMemory}.
     */
    public static class Builder {
        private Object id = ChatMemoryService.DEFAULT;
        private Function<Object, Integer> maxMessagesFunction;
        private Function<Object, Integer> summarizeThresholdFunction;
        private Function<Object, String> systemPromptFunction;
        private ChatMemoryStore store;
        private ChatModel chatModel;

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
         * Sets a dynamic function to determine when summarization should occur.
         * <p>
         * When the number of messages exceeds the configured {@code maxMessages},
         * this function is evaluated to decide the summarization threshold.
         * Messages beyond the threshold are removed, summarized,
         * and the summary message is inserted back into the message list.
         *
         * @param func a function that determines the summarization threshold based on the context or ID
         *             Must return a value greater than 1.
         * @return the builder instance
         */
        public Builder dynamicSummarizeThreshold(Function<Object, Integer> func) {
            this.summarizeThresholdFunction = func;
            return this;
        }

        /**
         * Sets a static summarization threshold.
         * <p>
         * When the number of messages exceeds the configured {@code maxMessages},
         * and this static threshold is reached, messages beyond the threshold will be
         * removed and summarized. The generated summary message will then be inserted
         * back into the message list.
         *
         * @param summarizeThreshold the fixed number of messages that triggers summarization
         *                           Must greater than 1.
         * @return the builder instance
         */
        public Builder summarizeThreshold(Integer summarizeThreshold) {
            this.summarizeThresholdFunction = (id) -> summarizeThreshold;
            return this;
        }

        /**
         * Sets a static system prompt used during summarization.
         * <p>
         * The specified prompt will be applied each time a summarization is performed,
         * providing consistent system-level instructions for generating summary content.
         *
         * @param systemPrompt the static system prompt to use during summarization
         *                     If not provided, an {@code DEFAULT_SYSTEM_PROMPT} will be used.
         * @return the builder instance
         */
        public Builder systemPrompt(String systemPrompt) {
            this.systemPromptFunction = (id) -> systemPrompt;
            return this;
        }

        /**
         * Sets a dynamic system prompt generator used during summarization.
         * <p>
         * The provided function is invoked to dynamically generate a system prompt
         * based on the given context or identifier each time a summarization occurs.
         * This allows different system instructions to be used for different sessions or contexts.
         *
         * @param systemPromptFunction a function that generates the system prompt dynamically
         *                             If not provided, an {@code DEFAULT_SYSTEM_PROMPT} will be used.
         * @return the builder instance
         */
        public Builder dynamicSystemPrompt(Function<Object, String> systemPromptFunction) {
            this.systemPromptFunction = systemPromptFunction;
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
         * Sets the chat model used for summarizing removed conversations.
         * <p>
         * When the number of messages exceeds the configured limits and older messages
         * need to be summarized, this {@link ChatModel} will be used to generate
         * the summary content for the removed conversation.
         *
         * @param chatModel the chat model used to summarize removed messages
         * @return the builder instance
         */
        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        private Function<Object, String> getSystemPromptFunction() {
            return systemPromptFunction != null ? systemPromptFunction : (id) -> DEFAULT_SYSTEM_PROMPT;
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
