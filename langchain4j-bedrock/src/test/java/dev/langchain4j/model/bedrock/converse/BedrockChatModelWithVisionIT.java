package dev.langchain4j.model.bedrock.converse;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;

import java.util.List;

import static dev.langchain4j.model.bedrock.converse.BedrockChatModel.dblToFloat;
import static dev.langchain4j.model.bedrock.converse.TestedModels.CLAUDE_3_HAIKU;

public class BedrockChatModelWithVisionIT extends AbstractChatModelIT {
    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(CLAUDE_3_HAIKU); //, LLAMA_3_2_90B); NOT AVAILABLE FOR ME AT THIS MOMENT
    }

    @Override
    protected String customModelName() {
        return "cohere.command-r-v1:0";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
    }

    @Override
    protected ChatLanguageModel createModelWith(ChatRequestParameters parameters) {
        // TODO
        return BedrockChatModel.builder()
                //force a working model with stopSequence parameter for @Tests
                .modelId("cohere.command-r-v1:0")
                .stopSequences(parameters.stopSequences())
                .temperature(dblToFloat(parameters.temperature()))
                .topP(dblToFloat(parameters.topP()))
                .maxTokens(parameters.maxOutputTokens())
                .build();
    }

    @Override
    protected boolean supportsDefaultRequestParameters() {
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false;
    }

}
