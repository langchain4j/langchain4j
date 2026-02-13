package dev.langchain4j.model.bedrock.common;

import static dev.langchain4j.model.bedrock.TestedModels.CLAUDE_3_HAIKU;
import static dev.langchain4j.model.bedrock.common.BedrockAiServicesIT.sleepIfNeeded;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatResponseMetadata;
import dev.langchain4j.model.bedrock.BedrockTokenUsage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockChatModelWithVisionIT extends AbstractChatModelIT {

    @Override
    protected List<ChatModel> models() {
        return List.of(CLAUDE_3_HAIKU); // , LLAMA_3_2_90B); NOT AVAILABLE FOR ME AT THIS MOMENT
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
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        return BedrockChatModel.builder()
                .defaultRequestParameters(parameters)
                // force a working model with stopSequence parameter for @Tests
                .modelId("cohere.command-r-v1:0")
                .build();
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(ChatModel model) {
        return BedrockTokenUsage.class;
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        return false; // output format not supported
    }



    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(final ChatModel model) {
        return BedrockChatResponseMetadata.class;
    }

    @AfterEach
    void afterEach() {
        sleepIfNeeded();
    }
}
