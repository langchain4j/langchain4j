package dev.langchain4j.model.chat.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ChatMessage;
import lombok.Builder;

import java.util.List;

/**
 * TODO
 */
@Builder
@Experimental
public class ChatLanguageModelRequest {

    private final String system; // gen_ai.system TODO provider?
    private final String modelName; // gen_ai.request.model

    // TODO group into "Parameters" POJO and re-use in ChatLanguageModel.generate()?
    private final Double temperature; // gen_ai.request.temperature
    private final Double topP; // gen_ai.request.top_p
    private final Integer maxTokens; // gen_ai.request.max_tokens

    // event
    private final List<ChatMessage> messages; // gen_ai.prompt
    private final List<ToolSpecification> toolSpecifications;

    public List<ChatMessage> messages() {
        return messages;
    }

    // TODO other getters
}
