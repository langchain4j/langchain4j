package dev.langchain4j.model.bedrock.internal;

import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Bedrock Chat model response
 */
public interface BedrockChatModelResponse {

    String getOutputText();

    FinishReason getFinishReason();

    TokenUsage getTokenUsage();

}
