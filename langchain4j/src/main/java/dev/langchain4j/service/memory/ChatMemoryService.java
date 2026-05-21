package dev.langchain4j.service.memory;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import java.util.Collection;
import java.util.Map;
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
}
