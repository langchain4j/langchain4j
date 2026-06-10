package dev.langchain4j.memory.chat;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.memory.ChatMemoryService;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A {@link ChatMemory} implementation that automatically compacts messages by summarizing them
 * using a {@link ChatModel}. Every time a {@link UserMessage} is added, a background compaction
 * task is triggered (unless a {@link Builder#compactionInterval(int)} is configured).
 * The compaction uses the provided {@link ChatModel} to produce a summarized
 * {@link UserMessage} that replaces older messages.
 * <p>
 * During compaction, {@link #messages()} returns the current (pre-compaction) state of messages.
 * Once compaction completes, the memory is atomically updated.
 * <p>
 * A {@link SystemMessage}, if present, is preserved across compactions and is not included
 * in the summarization. It is always kept as the first message.
 * <p>
 * Configuration options:
 * <ul>
 *   <li>{@link Builder#retainLastMessages(int)} — keep the last N conversation messages intact,
 *       summarizing only older messages.</li>
 *   <li>{@link Builder#maxTokens(int)} + {@link Builder#tokenCountEstimator(TokenCountEstimator)} —
 *       trigger compaction only when the token count exceeds the threshold.</li>
 *   <li>{@link Builder#compactToolMessages(boolean)} — when {@code false}, preserve tool call/result
 *       message pairs as-is instead of summarizing them.</li>
 *   <li>{@link Builder#compactionInterval(int)} — trigger compaction every N user messages
 *       instead of on every user message.</li>
 *   <li>{@link Builder#compactionPrompt(String)} — customize the summarization prompt.</li>
 * </ul>
 * <p>
 * The state of chat memory is stored in {@link ChatMemoryStore} ({@link SingleSlotChatMemoryStore} is used by default).
 * <p>
 * The compaction runs on a separate thread, taken from either a user-provided {@link ExecutorService}
 * or the default one from {@link DefaultExecutorProvider}.
 */
public class CompactingChatMemory implements ChatMemory {

    private static final String COMPACTION_PROMPT =
            """
            Summarize all former chat messages into a single concise message that preserves
            all important information, context, decisions, and any pending questions or tasks.
            The summary should be written from the user's perspective, as if the user is
            recapping the conversation so far. Do not include any preamble, just provide the summary.
            """;

    private final Object id;
    private final ChatModel chatModel;
    private final String compactionPrompt;
    private final int retainLastMessages;
    private final int maxTokens;
    private final TokenCountEstimator tokenCountEstimator;
    private final boolean compactToolMessages;
    private final int compactionInterval;
    private final ChatMemoryStore store;
    private final ExecutorService executorService;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final AtomicInteger userMessageCounter = new AtomicInteger(0);

    private CompactingChatMemory(Builder builder) {
        this.id = ensureNotNull(builder.id, "id");
        this.chatModel = ensureNotNull(builder.chatModel, "chatModel");
        this.compactionPrompt = getOrDefault(builder.compactionPrompt, COMPACTION_PROMPT);
        this.retainLastMessages = getOrDefault(builder.retainLastMessages, 0);
        this.compactToolMessages = getOrDefault(builder.compactToolMessages, true);
        this.compactionInterval = getOrDefault(builder.compactionInterval, 1);
        this.maxTokens = getOrDefault(builder.maxTokens, 0);
        this.tokenCountEstimator = builder.tokenCountEstimator;
        this.store = ensureNotNull(builder.store(), "store");
        this.executorService = getOrDefault(
                builder.executorService,
                DefaultExecutorProvider.getDefaultExecutorService());

        if (this.retainLastMessages < 0) {
            throw new IllegalArgumentException("retainLastMessages must be >= 0");
        }
        if (this.maxTokens > 0 && this.tokenCountEstimator == null) {
            throw new IllegalArgumentException("tokenCountEstimator must be provided when maxTokens is set");
        }
        if (this.compactionInterval < 1) {
            throw new IllegalArgumentException("compactionInterval must be >= 1");
        }
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public void add(ChatMessage message) {
        lock.writeLock().lock();
        try {
            List<ChatMessage> messages = new ArrayList<>(store.getMessages(id));

            if (message instanceof SystemMessage) {
                // Replace existing system message if present
                messages.removeIf(SystemMessage.class::isInstance);
                messages.add(0, message);
                store.updateMessages(id, messages);
                return;
            }
            messages.add(message);
            store.updateMessages(id, messages);
        } finally {
            lock.writeLock().unlock();
        }

        if (message instanceof UserMessage) {
            int count = userMessageCounter.incrementAndGet();
            if (count >= compactionInterval) {
                userMessageCounter.set(0);
                triggerCompaction();
            }
        }
    }

    @Override
    public List<ChatMessage> messages() {
        lock.readLock().lock();
        try {
            return store.getMessages(id);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            store.deleteMessages(id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void triggerCompaction() {
        // Take a snapshot under the read lock; all other computation happens in the background
        List<ChatMessage> snapshot;
        lock.readLock().lock();
        try {
            snapshot = new ArrayList<>(store.getMessages(id));
        } finally {
            lock.readLock().unlock();
        }

        if (snapshot.size() <= 1) {
            return; // Nothing to compact if there's only one message (or none)
        }

        executorService.submit(() -> {
            try {
                prepareAndCompact(snapshot);
            } catch (Exception e) {
                // Compaction failure is non-fatal; messages remain as they are
            }
        });
    }

    private void prepareAndCompact(List<ChatMessage> snapshot) {
        SystemMessage systemMessage = snapshot.stream()
                .filter(SystemMessage.class::isInstance)
                .map(SystemMessage.class::cast)
                .findFirst()
                .orElse(null);

        // Remove system message from the snapshot to work only with conversation messages
        List<ChatMessage> conversationMessages = new ArrayList<>();
        for (ChatMessage msg : snapshot) {
            if (!(msg instanceof SystemMessage)) {
                conversationMessages.add(msg);
            }
        }

        if (conversationMessages.size() <= 1) {
            return; // Only one conversation message, nothing to compact
        }

        // If maxTokens is configured, check whether compaction is needed
        if (maxTokens > 0) {
            int currentTokens = tokenCountEstimator.estimateTokenCountInMessages(conversationMessages);
            if (currentTokens <= maxTokens) {
                return; // Under the token limit, no compaction needed
            }
        }

        // Split into messages to summarize vs. messages to retain
        List<ChatMessage> toSummarize;
        List<ChatMessage> toRetain;

        if (maxTokens > 0) {
            // Token-based split: keep as many recent messages as fit within maxTokens
            toRetain = new ArrayList<>();
            int retainedTokens = 0;
            for (int i = conversationMessages.size() - 1; i >= 0; i--) {
                ChatMessage msg = conversationMessages.get(i);
                int msgTokens = tokenCountEstimator.estimateTokenCountInMessage(msg);
                if (retainedTokens + msgTokens <= maxTokens) {
                    toRetain.add(0, msg);
                    retainedTokens += msgTokens;
                } else {
                    break;
                }
            }
            int splitIndex = conversationMessages.size() - toRetain.size();
            toSummarize = new ArrayList<>(conversationMessages.subList(0, splitIndex));
        } else if (retainLastMessages > 0) {
            // Message-count-based split
            int splitIndex = Math.max(0, conversationMessages.size() - retainLastMessages);
            toSummarize = new ArrayList<>(conversationMessages.subList(0, splitIndex));
            toRetain = new ArrayList<>(conversationMessages.subList(splitIndex, conversationMessages.size()));
        } else {
            // Summarize everything
            toSummarize = conversationMessages;
            toRetain = new ArrayList<>();
        }

        // If compactToolMessages is false, move tool call pairs from toSummarize to toRetain
        if (!compactToolMessages && !toSummarize.isEmpty()) {
            List<ChatMessage> filteredSummarize = new ArrayList<>();
            List<ChatMessage> toolPairsToRetain = new ArrayList<>();
            for (int i = 0; i < toSummarize.size(); i++) {
                ChatMessage msg = toSummarize.get(i);
                if (msg instanceof AiMessage aiMsg && aiMsg.hasToolExecutionRequests()) {
                    // Keep this AiMessage and all following ToolExecutionResultMessages
                    toolPairsToRetain.add(msg);
                    int j = i + 1;
                    while (j < toSummarize.size()
                            && toSummarize.get(j) instanceof ToolExecutionResultMessage) {
                        toolPairsToRetain.add(toSummarize.get(j));
                        j++;
                    }
                    i = j - 1; // skip over consumed tool results
                } else if (msg instanceof ToolExecutionResultMessage) {
                    // Orphan tool result in the summarize zone - retain it to be safe
                    toolPairsToRetain.add(msg);
                } else {
                    filteredSummarize.add(msg);
                }
            }
            toSummarize = filteredSummarize;
            // Prepend tool pairs before the retained tail messages
            toolPairsToRetain.addAll(toRetain);
            toRetain = toolPairsToRetain;
        }

        if (toSummarize.size() <= 1) {
            return; // Not enough messages left to summarize
        }

        compact(toSummarize, toRetain, systemMessage);
    }

    private void compact(List<ChatMessage> messagesToSummarize, List<ChatMessage> retainedMessages,
            SystemMessage systemMessage) {
        // Build the summarization request: include messages to summarize + summarization instruction
        List<ChatMessage> request = new ArrayList<>(messagesToSummarize);
        request.add(UserMessage.from(compactionPrompt));

        String summary = chatModel.chat(request).aiMessage().text();

        List<ChatMessage> compacted = new ArrayList<>();
        if (systemMessage != null) {
            compacted.add(systemMessage);
        }
        compacted.add(UserMessage.from(summary));
        compacted.addAll(retainedMessages);

        lock.writeLock().lock();
        try {
            store.updateMessages(id, compacted);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Object id = ChatMemoryService.DEFAULT;
        private ChatModel chatModel;
        private String compactionPrompt;
        private Integer retainLastMessages;
        private Integer maxTokens;
        private TokenCountEstimator tokenCountEstimator;
        private Boolean compactToolMessages;
        private Integer compactionInterval;
        private ChatMemoryStore store;
        private ExecutorService executorService;

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
         * @param chatModel The {@link ChatModel} used to summarize messages during compaction.
         * @return builder
         */
        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        /**
         * @param compactionPrompt The prompt used to instruct the {@link ChatModel} to summarize messages.
         *                         If not provided, a default summarization prompt is used.
         * @return builder
         */
        public Builder compactionPrompt(String compactionPrompt) {
            this.compactionPrompt = compactionPrompt;
            return this;
        }

        /**
         * @param retainLastMessages The number of most recent conversation messages to keep intact.
         *                           Older messages will be summarized. If not provided or 0,
         *                           all conversation messages are summarized.
         * @return builder
         */
        public Builder retainLastMessages(int retainLastMessages) {
            this.retainLastMessages = retainLastMessages;
            return this;
        }

        /**
         * @param maxTokens The maximum number of tokens allowed before compaction is triggered.
         *                  Requires {@link #tokenCountEstimator(TokenCountEstimator)} to be set.
         *                  When compacting, as many recent messages as fit within this limit are retained.
         * @return builder
         */
        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * @param tokenCountEstimator The estimator used to count tokens in messages.
         *                            Required when {@link #maxTokens(int)} is set.
         * @return builder
         */
        public Builder tokenCountEstimator(TokenCountEstimator tokenCountEstimator) {
            this.tokenCountEstimator = tokenCountEstimator;
            return this;
        }

        /**
         * @param compactToolMessages Whether tool call/result message pairs should be included
         *                            in the summarization. If {@code false}, {@link AiMessage}s containing
         *                            tool execution requests and their corresponding
         *                            {@link ToolExecutionResultMessage}s are preserved as-is.
         *                            Defaults to {@code true}.
         * @return builder
         */
        public Builder compactToolMessages(boolean compactToolMessages) {
            this.compactToolMessages = compactToolMessages;
            return this;
        }

        /**
         * @param compactionInterval The number of user messages between compaction triggers.
         *                           For example, a value of 3 means compaction runs every 3rd user message.
         *                           Defaults to 1 (compact on every user message).
         * @return builder
         */
        public Builder compactionInterval(int compactionInterval) {
            this.compactionInterval = compactionInterval;
            return this;
        }

        /**
         * @param store The chat memory store responsible for storing the chat memory state.
         *              If not provided, a {@link SingleSlotChatMemoryStore} will be used.
         * @return builder
         */
        public Builder chatMemoryStore(ChatMemoryStore store) {
            this.store = store;
            return this;
        }

        private ChatMemoryStore store() {
            return store != null ? store : new SingleSlotChatMemoryStore(id);
        }

        /**
         * @param executorService The {@link ExecutorService} used to run compaction tasks.
         *                        If not provided, the default from {@link DefaultExecutorProvider} is used.
         * @return builder
         */
        public Builder executorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        public CompactingChatMemory build() {
            return new CompactingChatMemory(this);
        }
    }
}
