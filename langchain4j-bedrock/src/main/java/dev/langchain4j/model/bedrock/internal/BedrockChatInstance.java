package dev.langchain4j.model.bedrock.internal;

import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

public interface BedrockChatInstance {

    String getOutputText();

    FinishReason getFinishReason();

    TokenUsage getTokenUsage();

}
