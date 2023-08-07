package dev.langchain4j.service.context;

import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.retriever.Retriever;

import java.util.List;
import java.util.Map;

public interface BaseAiServiceContext {

    ChatMemory getChatMemory();

    ModerationModel getModerationModel();

    List<ToolSpecification> getToolSpecifications();
    Map<String, ToolExecutor> getToolExecutors();

    Retriever<TextSegment> getRetriever();
}
