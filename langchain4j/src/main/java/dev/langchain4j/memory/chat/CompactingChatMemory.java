package dev.langchain4j.memory.chat;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.DefaultExecutorProvider;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.memory.ChatMemoryService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A {@link ChatMemory} implementation that automatically compacts messages by summarizing them
 * using a {@link ChatModel}. Every time a {@link UserMessage} is added, a background compaction
 * task is triggered. The compaction uses the provided {@link ChatModel} to produce a summarized
 * {@link UserMessage} that replaces all current messages.
 * <p>
 * During compaction, {@link #messages()} returns the current (pre-compaction) state of messages.
 * Once compaction completes, the memory is atomically replaced with a single summarized
 * {@link UserMessage}.
 * <p>
 * A {@link SystemMessage}, if present, is preserved across compactions and is not included
 * in the summarization. It is always kept as the first message.
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
    private final ExecutorService executorService;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final List<ChatMessage> messages = new ArrayList<>();

    private CompactingChatMemory(Builder builder) {
        this.id = ensureNotNull(builder.id, "id");
        this.chatModel = ensureNotNull(builder.chatModel, "chatModel");
        this.executorService = getOrDefault(
                builder.executorService,
                DefaultExecutorProvider.getDefaultExecutorService());
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public void add(ChatMessage message) {
        lock.writeLock().lock();
        try {
            if (message instanceof SystemMessage) {
                // Replace existing system message if present
                messages.removeIf(SystemMessage.class::isInstance);
                messages.add(0, message);
                return;
            }
            messages.add(message);
        } finally {
            lock.writeLock().unlock();
        }

        if (message instanceof UserMessage) {
            triggerCompaction();
        }
    }

    @Override
    public List<ChatMessage> messages() {
        lock.readLock().lock();
        try {
            return Collections.unmodifiableList(messages);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            messages.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void triggerCompaction() {
        // Snapshot the messages to summarize
        List<ChatMessage> snapshot;
        SystemMessage systemMessage;
        lock.readLock().lock();
        try {
            if (messages.size() <= 1) {
                return; // Nothing to compact if there's only one message (or none)
            }
            snapshot = new ArrayList<>(messages);
            systemMessage = messages.stream()
                    .filter(SystemMessage.class::isInstance)
                    .map(SystemMessage.class::cast)
                    .findFirst()
                    .orElse(null);
        } finally {
            lock.readLock().unlock();
        }

        // Remove system message from the snapshot to summarize only conversation messages
        List<ChatMessage> conversationMessages = new ArrayList<>();
        for (ChatMessage msg : snapshot) {
            if (!(msg instanceof SystemMessage)) {
                conversationMessages.add(msg);
            }
        }

        if (conversationMessages.size() <= 1) {
            return; // Only one conversation message, nothing to compact
        }

        executorService.submit(() -> {
            try {
                compact(conversationMessages, systemMessage);
            } catch (Exception e) {
                // Compaction failure is non-fatal; messages remain as they are
            }
        });
    }

    private void compact(List<ChatMessage> conversationMessages, SystemMessage systemMessage) {
        // Build the summarization request: include all conversation messages + a summarization instruction
        List<ChatMessage> request = new ArrayList<>(conversationMessages);
        request.add(UserMessage.from(COMPACTION_PROMPT));

        String summary = chatModel.chat(request).aiMessage().text();

        lock.writeLock().lock();
        try {
            messages.clear();
            if (systemMessage != null) {
                messages.add(systemMessage);
            }
            messages.add(UserMessage.from(summary));
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
