package dev.langchain4j.service;

import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.rag.RetrievalAugmentor;

import java.util.*;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.enums;

public class AiServiceContext {

    public final Class<?> aiServiceClass;

    public ChatLanguageModel chatModel;
    public StreamingChatLanguageModel streamingChatModel;

    public Map</* id */ Object, ChatMemory> chatMemories;
    public ChatMemoryProvider chatMemoryProvider;

    public ModerationModel moderationModel;

    public List<ToolSpecification> toolSpecifications;
    public Map<String, List<ToolSpecification>> stateToToolSpecifications = new HashMap<>(); // TODO

    public List<ToolSpecification> toolSpecifications() {
        // TODO

        List<ToolSpecification> toolSpecifications = new ArrayList<>();
        ToolSpecification stateToolSpec = ToolSpecification.builder()
                .name("setState")
                .description("Sets new conversation state")
                .addParameter("state", enums(allowedTransitions.get(currentState)))
                .build();
        toolSpecifications.add(stateToolSpec);

        if (stateToToolSpecifications.containsKey(currentState)) {
            toolSpecifications.addAll(stateToToolSpecifications.get(currentState));
        }

        return toolSpecifications;
    }

    public Map<String, ToolExecutor> toolExecutors;

    public RetrievalAugmentor retrievalAugmentor;

    public Class<? extends Enum<?>> states;
    public String currentState; // TODO one for each user/conversation
    public Object stateObject; // TODO one for each user/conversation
    public Map<String, Set<String>> allowedTransitions;
    public Map<String, String> stateToSystemMessage;

    public AiServiceContext(Class<?> aiServiceClass) {
        this.aiServiceClass = aiServiceClass;
    }

    public boolean hasChatMemory() {
        return chatMemories != null;
    }

    public ChatMemory chatMemory(Object memoryId) {
        return chatMemories.computeIfAbsent(memoryId, ignored -> chatMemoryProvider.get(memoryId));
    }

    public SystemMessage systemMessage() {
        return SystemMessage.from(stateToSystemMessage.get(currentState)); // TODO
    }
}
