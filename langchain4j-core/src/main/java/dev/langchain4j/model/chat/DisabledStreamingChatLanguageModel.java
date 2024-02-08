package dev.langchain4j.model.chat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.StreamingResponseHandler;

import java.util.List;

/**
 * A {@link StreamingChatLanguageModel} which throws a {@link ModelDisabledException} for all of its methods
 * <p>
 *     This could be used in tests, or in libraries that extend this one to conditionally enable or disable functionality.
 * </p>
 */
public class DisabledStreamingChatLanguageModel implements StreamingChatLanguageModel {
    @Override
    public void generate(String userMessage, StreamingResponseHandler<AiMessage> handler) {
        throw new ModelDisabledException("StreamingChatLanguageModel is disabled");
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        throw new ModelDisabledException("StreamingChatLanguageModel is disabled");
    }

    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        throw new ModelDisabledException("StreamingChatLanguageModel is disabled");
    }

    @Override
    public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
        throw new ModelDisabledException("StreamingChatLanguageModel is disabled");
    }
}
