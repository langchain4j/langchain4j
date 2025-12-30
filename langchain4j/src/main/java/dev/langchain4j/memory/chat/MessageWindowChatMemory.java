package dev.langchain4j.memory.chat;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.service.memory.ChatMemoryService;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * This chat memory operates as a sliding window whose size is controlled by a {@link #maxMessagesProvider}.
 * It retains as many of the most recent messages as can fit into the window.
 * If there isn't enough space for a new message, the oldest one is evicted.
 * <p>
 * The maximum number of messages can be provided either statically or dynamically
 * through the {@code maxMessagesProvider}. When supplied dynamically, the effective
 * window size can change at runtime, and the sliding-window behavior always respects
 * the most recent value returned by the provider.
 * <p>
 * Once added, a {@link SystemMessage} is always retained.
 * If {@link MessageWindowChatMemory#systemMessageFirst} is true, always keep system message on index 0.
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
public class MessageWindowChatMemory implements ChatMemory {

    private final Object id;
    private final ChatMemoryStore store;
    private final Function<Object, Integer> maxMessagesProvider;
    private final boolean systemMessageFirst;

    private MessageWindowChatMemory(Builder builder) {
        this.id = ensureNotNull(builder.id, "id");
        this.maxMessagesProvider = ensureNotNull(builder.maxMessagesProvider, "maxMessagesProvider");
        this.store = ensureNotNull(builder.store(), "store");
        this.systemMessageFirst = builder.systemMessageFirst;
        ensureGreaterThanZero(this.maxMessagesProvider.apply(this.id), "maxMessages");
    }

    @Override
    public Object id() {
        return id;
    }

    @Override
    public void add(ChatMessage message) {
        Integer maxMessages = this.maxMessagesProvider.apply(this.id);
        ensureGreaterThanZero(maxMessages, "maxMessages");
        List<ChatMessage> messages = messages();
        if (message instanceof SystemMessage) {
            Optional<SystemMessage> systemMessage = SystemMessage.findFirst(messages);
            if (systemMessage.isPresent()) {
                if (systemMessage.get().equals(message)) {
                    return; // do not add the same system message
                } else {
                    messages.remove(systemMessage.get()); // need to replace existing system message
                }
            }
        }
        if (message instanceof SystemMessage && this.systemMessageFirst) {
            messages.add(0, message);
        } else {
            messages.add(message);
        }
        ensureCapacity(messages, maxMessages);
        store.updateMessages(id, messages);
    }

    @Override
    public List<ChatMessage> messages() {
        Integer maxMessages = this.maxMessagesProvider.apply(this.id);
        ensureGreaterThanZero(maxMessages, "maxMessages");
        List<ChatMessage> messages = new LinkedList<>(store.getMessages(id));
        ensureCapacity(messages, maxMessages);
        return messages;
    }

    private static void ensureCapacity(List<ChatMessage> messages, int maxMessages) {
        while (messages.size() > maxMessages) {

            int messageToEvictIndex = 0;
            if (messages.get(0) instanceof SystemMessage) {
                messageToEvictIndex = 1;
            }

            ChatMessage evictedMessage = messages.remove(messageToEvictIndex);
            if (evictedMessage instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                while (messages.size() > messageToEvictIndex
                        && messages.get(messageToEvictIndex) instanceof ToolExecutionResultMessage) {
                    // Some LLMs (e.g. OpenAI) prohibit ToolExecutionResultMessage(s) without corresponding AiMessage,
                    // so we have to automatically evict orphan ToolExecutionResultMessage(s) if AiMessage was evicted
                    messages.remove(messageToEvictIndex);
                }
            }
        }
    }

    @Override
    public void clear() {
        store.deleteMessages(id);
    }

    public boolean isSystemMessageFirst() {
        return systemMessageFirst;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean systemMessageFirst;
        private Object id = ChatMemoryService.DEFAULT;
        private Function<Object, Integer> maxMessagesProvider;
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
         * @param maxMessages The maximum number of messages to retain.
         *                    If there isn't enough space for a new message, the oldest one is evicted.
         * @return builder
         */
        public Builder maxMessages(Integer maxMessages) {
            this.maxMessagesProvider = (id) -> maxMessages;
            return this;
        }

        /**
         * @param maxMessagesProvider A provider that provides the maximum number of messages to retain.
         *                                   The returned value may change dynamically at runtime.
         *                                   If there isn't enough space for a new message under the current limit,
         *                                   the oldest one is evicted.
         * @return builder
         */
        public Builder dynamicMaxMessages(Function<Object, Integer> maxMessagesProvider) {
            this.maxMessagesProvider = maxMessagesProvider;
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
         * Specifies whether the system message should be added to the beginning of the message list.
         *
         * @param systemMessageFirst
         * @return
         */
        public Builder alwaysKeepSystemMessageFirst(boolean systemMessageFirst) {
            this.systemMessageFirst = systemMessageFirst;
            return this;
        }

        private ChatMemoryStore store() {
            return store != null ? store : new SingleSlotChatMemoryStore(id);
        }

        public MessageWindowChatMemory build() {
            return new MessageWindowChatMemory(this);
        }
    }

    public static MessageWindowChatMemory withMaxMessages(int maxMessages) {
        return builder().maxMessages(maxMessages).build();
    }
}
