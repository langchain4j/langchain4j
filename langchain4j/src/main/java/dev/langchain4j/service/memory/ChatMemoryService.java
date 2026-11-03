package dev.langchain4j.service.memory;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.CurrentInvocation;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Internal
public class ChatMemoryService {

    public static final String DEFAULT = "default";

    private ChatMemory defaultChatMemory;
    private Map<Object, ChatMemory> chatMemories;
    private ChatMemoryProvider chatMemoryProvider;

    public ChatMemoryService(ChatMemoryProvider chatMemoryProvider) {
        this.chatMemories = new ConcurrentHashMap<>();
        this.chatMemoryProvider = ensureNotNull(chatMemoryProvider, "chatMemoryProvider");
    }

    public ChatMemoryService(ChatMemory chatMemory) {
        defaultChatMemory = ensureNotNull(chatMemory, "chatMemory");
    }

    public ChatMemory getOrCreateChatMemory(Object memoryId) {
        if (chatMemoryProvider != null) {
            return chatMemories.computeIfAbsent(memoryId, chatMemoryProvider::get);
        }
        return defaultChatMemory;
    }

    /**
     * Returns the chat memory of the given invocation, wrapped so that every call to it (and so to its
     * {@code ChatMemoryStore}) runs as work of that invocation: an AI Service invoked from there gets it as its
     * parent invocation. The {@link ChatMemoryProvider} is called the same way.
     */
    public ChatMemory getOrCreateInvocationChatMemory(InvocationContext invocationContext) {
        ChatMemory chatMemory = CurrentInvocation.callWith(
                invocationContext, () -> getOrCreateChatMemory(invocationContext.chatMemoryId()));
        return new InvocationChatMemory(chatMemory, invocationContext);
    }

    /**
     * Returns the chat memory as configured by the user, without the wrapper added by
     * {@link #getOrCreateInvocationChatMemory(InvocationContext)}.
     */
    public static ChatMemory unwrap(ChatMemory chatMemory) {
        return chatMemory instanceof InvocationChatMemory invocationChatMemory
                ? invocationChatMemory.delegate()
                : chatMemory;
    }

    public ChatMemory getChatMemory(Object memoryId) {
        return chatMemoryProvider != null ? chatMemories.get(memoryId) : memoryId == DEFAULT ? defaultChatMemory : null;
    }

    public ChatMemory evictChatMemory(Object memoryId) {
        return chatMemories.remove(memoryId);
    }

    public void clearAll() {
        chatMemories.values().forEach(ChatMemory::clear);
        chatMemories.clear();
    }

    public Collection<Object> getChatMemoryIDs() {
        return chatMemories.keySet();
    }

    public Collection<ChatMemory> getChatMemories() {
        return chatMemories.values();
    }

    private record InvocationChatMemory(ChatMemory delegate, InvocationContext invocationContext)
            implements ChatMemory {

        @Override
        public Object id() {
            return delegate.id();
        }

        @Override
        public void add(ChatMessage message) {
            run(() -> delegate.add(message));
        }

        @Override
        public void add(ChatMessage... messages) {
            run(() -> delegate.add(messages));
        }

        @Override
        public void add(Iterable<ChatMessage> messages) {
            run(() -> delegate.add(messages));
        }

        @Override
        public void set(ChatMessage... messages) {
            run(() -> delegate.set(messages));
        }

        @Override
        public void set(Iterable<ChatMessage> messages) {
            run(() -> delegate.set(messages));
        }

        @Override
        public List<ChatMessage> messages() {
            return CurrentInvocation.callWith(invocationContext, delegate::messages);
        }

        @Override
        public CompletableFuture<Void> addAsync(List<ChatMessage> messages) {
            return CurrentInvocation.callWith(invocationContext, () -> delegate.addAsync(messages));
        }

        @Override
        public CompletableFuture<Void> setAsync(List<ChatMessage> messages) {
            return CurrentInvocation.callWith(invocationContext, () -> delegate.setAsync(messages));
        }

        @Override
        public CompletableFuture<List<ChatMessage>> messagesAsync() {
            return CurrentInvocation.callWith(invocationContext, delegate::messagesAsync);
        }

        @Override
        public void clear() {
            run(delegate::clear);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }

        private void run(Runnable action) {
            CurrentInvocation.callWith(invocationContext, () -> {
                action.run();
                return null;
            });
        }
    }
}
