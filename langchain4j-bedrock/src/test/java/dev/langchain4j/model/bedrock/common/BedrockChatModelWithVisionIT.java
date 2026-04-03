package dev.langchain4j.model.bedrock.common;

import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatResponseMetadata;
import dev.langchain4j.model.bedrock.BedrockTokenUsage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static dev.langchain4j.model.bedrock.common.BedrockAiServicesIT.sleepIfNeeded;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockChatModelWithVisionIT extends AbstractChatModelIT {

    static ChatModel model = BedrockChatModel.builder()
            .modelId("us.anthropic.claude-haiku-4-5-20251001-v1:0")
            .logRequests(false) // images are huge in logs
            .logResponses(true)
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(model);
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
        return false; // JSON response format *without schema* is not supported
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
