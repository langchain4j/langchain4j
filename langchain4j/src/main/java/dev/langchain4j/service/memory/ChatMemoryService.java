package dev.langchain4j.service.memory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

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
        return memoryId == DEFAULT ? defaultChatMemory : chatMemories.computeIfAbsent(memoryId, chatMemoryProvider::get);
    }

    public ChatMemory getChatMemory(Object memoryId) {
        return memoryId == DEFAULT ? defaultChatMemory : chatMemories.get(memoryId);
    }

    public ChatMemory evictChatMemory(Object memoryId) {
        return chatMemories.remove(memoryId);
    }
}
