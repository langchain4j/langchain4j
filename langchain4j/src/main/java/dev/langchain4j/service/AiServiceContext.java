package dev.langchain4j.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.StorelessChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.tool.ToolService;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class AiServiceContext {

    private static final Function<Object, Optional<String>> DEFAULT_MESSAGE_PROVIDER = x -> Optional.empty();

    public final Class<?> aiServiceClass;

    public ChatLanguageModel chatModel;
    public StreamingChatLanguageModel streamingChatModel;

    private ChatMemory defaultChatMemory;
    private ChatMemoryProvider chatMemoryProvider;

    // used only when a store for the chat memory is not explicitly provided
    private final ChatMemoryStore contextStore = new InMemoryChatMemoryStore();

    public ToolService toolService = new ToolService();

    public ModerationModel moderationModel;

    public RetrievalAugmentor retrievalAugmentor;

    public Function<Object, Optional<String>> systemMessageProvider = DEFAULT_MESSAGE_PROVIDER;

    public AiServiceContext(Class<?> aiServiceClass) {
        this.aiServiceClass = aiServiceClass;
    }

    public boolean hasChatMemory() {
        return defaultChatMemory != null || chatMemoryProvider != null;
    }

    public boolean hasChatMemoryProvider() {
        return chatMemoryProvider != null;
    }

    public void initChatMemoryProvider(ChatMemoryProvider chatMemoryProvider) {
        checkNotInitialized();
        this.chatMemoryProvider = chatMemoryProvider;
    }

    public void initDefaultChatMemory(ChatMemory defaultChatMemory) {
        checkNotInitialized();
        this.defaultChatMemory = defaultChatMemory instanceof StorelessChatMemory storeless
                ? storeless.withStore(new SingleEntryChatMemoryStore())
                : defaultChatMemory;
    }

    private void checkNotInitialized() {
        if (this.chatMemoryProvider != null) {
            throw new IllegalStateException("Chat memory provider already set");
        }
        if (this.defaultChatMemory != null) {
            throw new IllegalStateException("Default chat memory already set");
        }
    }

    public ChatMemory chatMemory(Object memoryId) {
        if (defaultChatMemory != null && memoryId.equals(AiServices.DEFAULT)) {
            return defaultChatMemory;
        }
        ChatMemory chatMemory = chatMemoryProvider.get(memoryId);
        return chatMemory instanceof StorelessChatMemory storeless ? storeless.withStore(contextStore) : chatMemory;
    }

    static class SingleEntryChatMemoryStore implements ChatMemoryStore {

        List<ChatMessage> chatMessages = new ArrayList<>();

        @Override
        public List<ChatMessage> getMessages(final Object memoryId) {
            return chatMessages;
        }

        @Override
        public void updateMessages(final Object memoryId, final List<ChatMessage> messages) {
            this.chatMessages = messages;
        }

        @Override
        public void deleteMessages(final Object memoryId) {
            chatMessages.clear();
        }
    }
}
