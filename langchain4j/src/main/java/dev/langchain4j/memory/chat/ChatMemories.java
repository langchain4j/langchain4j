package dev.langchain4j.memory.chat;

import dev.langchain4j.memory.ChatMemory;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public class ChatMemories {

    public static final String DEFAULT = "default";

    private ChatMemory defaultChatMemories;
    private Map<Object, ChatMemory> chatMemories;
    private ChatMemoryProvider chatMemoryProvider;

    public ChatMemories(ChatMemoryProvider chatMemoryProvider) {
        this.chatMemories = Collections.synchronizedMap(new WeakHashMap<>());
        this.chatMemoryProvider = chatMemoryProvider;
    }

    public ChatMemories(ChatMemory chatMemory) {
        defaultChatMemories = chatMemory;
    }

    public ChatMemory chatMemory(Object memoryId) {
        if (memoryId == DEFAULT) {
            return defaultChatMemories;
        }
        return chatMemories.computeIfAbsent(memoryId, ignored -> createChatMemory(memoryId));
    }

    private ChatMemory createChatMemory(Object memoryId) {
        ChatMemory chatMemory = chatMemoryProvider.get(memoryId);
        if (chatMemory instanceof RemovableChatMemory rcm) {
            rcm.onChatMemoryRemove(chatMemories::remove);
        }
        return chatMemory;
    }
}
