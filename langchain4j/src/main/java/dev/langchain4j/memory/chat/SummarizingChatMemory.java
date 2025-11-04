package dev.langchain4j.memory.chat;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.memory.ChatMemoryService;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

/**
 * A summarizing chat memory that automatically compresses conversation history
 * when the message count exceeds the configured threshold.
 * <p>
 * This class supports both static and dynamic injection of {@code maxMessages} and
 * {@code summarizeThreshold} through function references. It can also generate
 * a dynamic system prompt per memory ID.
 */
public class SummarizingChatMemory implements ChatMemory {

    private final Object id;
    private final ChatMemoryStore store;
    private final ChatModel chatModel;

    private final Function<Object, Integer> maxMessagesFunction;
    private final Function<Object, Integer> summarizeThresholdFunction;
    private final Function<Object, String> systemPromptFunction;

    /** Default system prompt used to summarize conversations */
    private static final String DEFAULT_SYSTEM_PROMPT = """
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

    // -------------------- Constructor --------------------

    private SummarizingChatMemory(Builder builder) {
        this.id = ensureNotNull(builder.id, "id");
        this.store = ensureNotNull(builder.store, "store");
        this.chatModel = ensureNotNull(builder.chatModel, "chatModel");

        this.systemPromptFunction = builder.systemPromptFunction == null
                ? (id) -> DEFAULT_SYSTEM_PROMPT
                : builder.systemPromptFunction;

        this.maxMessagesFunction = ensureNotNull(builder.maxMessagesFunction, "maxMessagesFunction");
        this.summarizeThresholdFunction = ensureNotNull(builder.summarizeThresholdFunction, "summarizeThresholdFunction");

        // Validate configuration once on build
        int max = maxMessagesFunction.apply(id);
        int threshold = summarizeThresholdFunction.apply(id);
        ensureGreaterThanZero(max, "maxMessages");
        ensureGreaterThanZero(threshold, "summarizeThreshold");
        ensureGreaterThanZero(max - threshold, "maxMessages - summarizeThreshold");
    }

    // -------------------- Public API --------------------

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

    // -------------------- Internal Logic --------------------

    /**
     * Check message list size and trigger summarization if it exceeds threshold.
     */
    private void checkAndSummarizeMessages(List<ChatMessage> messages) {
        int maxMessages = maxMessagesFunction.apply(id);
        int summarizeThreshold = summarizeThresholdFunction.apply(id);

        ensureGreaterThanZero(maxMessages, "maxMessages");
        ensureGreaterThanZero(summarizeThreshold, "summarizeThreshold");
        ensureGreaterThanZero(maxMessages - summarizeThreshold, "maxMessages - summarizeThreshold");

        if (messages.size() <= maxMessages) return;

        List<ChatMessage> removedMessages = new ArrayList<>();
        while (messages.size() > maxMessages - summarizeThreshold) {
            int evictIndex = (messages.get(0) instanceof SystemMessage) ? 1 : 0;

            ChatMessage evicted = messages.remove(evictIndex);
            removedMessages.add(evicted);

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
        messages.addFirst(systemPrompt);
        ChatResponse chatResponse = chatModel.chat(messages);
        return chatResponse.aiMessage().withText(chatResponse.aiMessage().text());
    }

    // -------------------- Builder --------------------

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

        public Builder id(Object id) {
            this.id = id;
            return this;
        }

        /** Dynamically determine max message count by ID */
        public Builder dynamicMaxMessages(Function<Object, Integer> func) {
            this.maxMessagesFunction = func;
            return this;
        }

        /** Dynamically determine summarization threshold by ID */
        public Builder dynamicSummarizeThreshold(Function<Object, Integer> func) {
            this.summarizeThresholdFunction = func;
            return this;
        }

        /** Set a static max message count */
        public Builder maxMessages(Integer maxMessages) {
            this.maxMessagesFunction = (id) -> maxMessages;
            return this;
        }

        /** Set a static summarization threshold */
        public Builder summarizeThreshold(Integer summarizeThreshold) {
            this.summarizeThresholdFunction = (id) -> summarizeThreshold;
            return this;
        }

        /** Set a static system prompt */
        public Builder systemPrompt(String systemPrompt) {
            this.systemPromptFunction = (id) -> systemPrompt;
            return this;
        }

        /** Set a dynamic system prompt generator */
        public Builder dynamicSystemPrompt(Function<Object, String> systemPromptFunction) {
            this.systemPromptFunction = systemPromptFunction;
            return this;
        }

        public Builder chatMemoryStore(ChatMemoryStore store) {
            this.store = store;
            return this;
        }

        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public SummarizingChatMemory build() {
            return new SummarizingChatMemory(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
