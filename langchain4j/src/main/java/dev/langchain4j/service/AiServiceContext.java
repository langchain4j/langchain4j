package dev.langchain4j.service;

import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.rag.RetrievalAugmentor;

import java.util.List;
import java.util.Map;

public class AiServiceContext {

    public final Class<?> aiServiceClass;

    public ChatLanguageModel chatModel;
    public StreamingChatLanguageModel streamingChatModel;

    public Map</* id */ Object, ChatMemory> chatMemories;
    public ChatMemoryProvider chatMemoryProvider;

    public ModerationModel moderationModel;

    public List<ToolSpecification> toolSpecifications;
    public Map<String, ToolExecutor> toolExecutors;

    public RetrievalAugmentor retrievalAugmentor;

    public AiServiceContext(Class<?> aiServiceClass) {
        this.aiServiceClass = aiServiceClass;
    }

    public boolean hasChatMemory() {
        return chatMemories != null;
    }

    public ChatMemory chatMemory(Object memoryId) {
        return chatMemories.computeIfAbsent(memoryId, ignored -> chatMemoryProvider.get(memoryId));
    }
}
