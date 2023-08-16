package dev.langchain4j.service;

import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.retriever.Retriever;

import java.util.List;
import java.util.Map;

class AiServiceContext {

    Class<?> aiServiceClass;

    ChatLanguageModel chatLanguageModel;
    StreamingChatLanguageModel streamingChatLanguageModel;

    Map</* userId */ Object, ChatMemory> chatMemories;
    ChatMemoryProvider chatMemoryProvider;

    ModerationModel moderationModel;

    List<ToolSpecification> toolSpecifications;
    Map<String, ToolExecutor> toolExecutors;

    Retriever<TextSegment> retriever;

    boolean hasChatMemory() {
        return chatMemories != null;
    }

    ChatMemory chatMemoryOf(Object userId) {
        return chatMemories.computeIfAbsent(userId, ignored -> chatMemoryProvider.chatMemoryOf(userId));
    }
}
