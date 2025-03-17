package dev.langchain4j.memory.chat;

import dev.langchain4j.memory.ChatMemory;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class ChatMemoryService {

    public static final String DEFAULT = "default";

    private ChatMemory defaultChatMemory;
    private Map<Object, ChatMemory> chatMemories;
    private ChatMemoryProvider chatMemoryProvider;

    public ChatMemoryService(ChatMemoryProvider chatMemoryProvider) {
        this.chatMemories = Collections.synchronizedMap(new WeakHashMap<>());
        this.chatMemoryProvider = ensureNotNull(chatMemoryProvider, "chatMemoryProvider");
    }

    public ChatMemoryService(ChatMemory chatMemory) {
        defaultChatMemory = ensureNotNull(chatMemory, "chatMemory");
    }

    public ChatMemory chatMemory(Object memoryId) {
        if (memoryId == DEFAULT) {
            return defaultChatMemory;
        }
        return chatMemories.computeIfAbsent(memoryId, ignored -> createChatMemory(memoryId));
    }

    private ChatMemory createChatMemory(Object memoryId) {
        ChatMemory chatMemory = chatMemoryProvider.get(memoryId);
        if (chatMemory instanceof RemovalAwareChatMemory rcm) {
            rcm.onChatMemoryRemove(chatMemories::remove);
        }
        return chatMemory;
    }
}
