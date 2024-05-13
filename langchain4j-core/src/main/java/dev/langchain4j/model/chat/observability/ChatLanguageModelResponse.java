package dev.langchain4j.model.chat.observability;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import lombok.Builder;

/**
 * TODO
 */
@Builder
@Experimental
public class ChatLanguageModelResponse {

    private final String id; // gen_ai.response.id
    private final String model; // gen_ai.response.model
    private final TokenUsage tokenUsage; // gen_ai.usage.completion_tokens + gen_ai.usage.prompt_tokens
    private final FinishReason finishReason; // gen_ai.response.finish_reasons

    // event
    private final AiMessage aiMessage; // gen_ai.completion

    public String id() {
        return id;
    }

    public String model() {
        return model;
    }

    public AiMessage aiMessage() {
        return aiMessage;
    }

    // TODO other getters
}
