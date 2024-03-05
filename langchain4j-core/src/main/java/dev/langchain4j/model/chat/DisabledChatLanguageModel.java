package dev.langchain4j.model.chat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.ModelDisabledException;
import dev.langchain4j.model.output.Response;

import java.util.List;

/**
 * A {@link ChatLanguageModel} which throws a {@link ModelDisabledException} for all of its methods
 * <p>
 *     This could be used in tests, or in libraries that extend this one to conditionally enable or disable functionality.
 * </p>
 */
public class DisabledChatLanguageModel implements ChatLanguageModel {
    @Override
    public String generate(String userMessage) {
        throw new ModelDisabledException("ChatLanguageModel is disabled");
    }

    @Override
    public Response<AiMessage> generate(ChatMessage... messages) {
        throw new ModelDisabledException("ChatLanguageModel is disabled");
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        throw new ModelDisabledException("ChatLanguageModel is disabled");
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        throw new ModelDisabledException("ChatLanguageModel is disabled");
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        throw new ModelDisabledException("ChatLanguageModel is disabled");
    }
}
