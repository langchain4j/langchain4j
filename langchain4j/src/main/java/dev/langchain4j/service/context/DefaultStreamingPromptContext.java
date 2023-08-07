package dev.langchain4j.service.context;

import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.retriever.Retriever;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Builder
@Getter
public class DefaultStreamingPromptContext implements StreamingPromptTemplateContext {

    private final PromptTemplate promptTemplate;
    private final ChatMemory chatMemory;
    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, ToolExecutor> toolExecutors;
    private final Retriever<TextSegment> retriever;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final ModerationModel moderationModel;
}
