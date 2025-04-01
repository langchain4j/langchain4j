package dev.langchain4j.service;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryService;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.service.tool.ToolService;
import java.util.Optional;
import java.util.function.Function;

public class AiServiceContext {

    private static final Function<Object, Optional<String>> DEFAULT_MESSAGE_PROVIDER = x -> Optional.empty();

    public final Class<?> aiServiceClass;

    public ChatLanguageModel chatModel;
    public StreamingChatLanguageModel streamingChatModel;

    public ChatMemoryService chatMemoryService;

    public ToolService toolService = new ToolService();

    public ModerationModel moderationModel;

    public RetrievalAugmentor retrievalAugmentor;

    public Function<Object, Optional<String>> systemMessageProvider = DEFAULT_MESSAGE_PROVIDER;

    public AiServiceContext(Class<?> aiServiceClass) {
        this.aiServiceClass = aiServiceClass;
    }

    public boolean hasChatMemory() {
        return chatMemoryService != null;
    }

    public void initChatMemories(ChatMemory chatMemory) {
        chatMemoryService = new ChatMemoryService(chatMemory);
    }

    public void initChatMemories(ChatMemoryProvider chatMemoryProvider) {
        chatMemoryService = new ChatMemoryService(chatMemoryProvider);
    }
}
