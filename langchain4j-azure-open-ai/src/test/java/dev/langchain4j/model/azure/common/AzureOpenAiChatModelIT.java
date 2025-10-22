package dev.langchain4j.model.azure.common;

import dev.langchain4j.model.azure.AzureModelBuilders;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiChatModelIT extends AbstractChatModelIT {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219

    static final AzureOpenAiChatModel AZURE_OPEN_AI_CHAT_MODEL = AzureModelBuilders.chatModelBuilder()
            .logRequestsAndResponses(false) // images are huge in logs
            .build();

    static final AzureOpenAiChatModel AZURE_OPEN_AI_CHAT_MODEL_STRICT_SCHEMA = AzureModelBuilders.chatModelBuilder()
            .strictJsonSchema(true)
            .logRequestsAndResponses(false) // images are huge in logs
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(AZURE_OPEN_AI_CHAT_MODEL, AZURE_OPEN_AI_CHAT_MODEL_STRICT_SCHEMA);
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        AzureOpenAiChatModel.Builder chatModelBuilder = AzureModelBuilders.chatModelBuilder()
                .defaultRequestParameters(parameters)
                .deploymentName(null)
                .maxTokens(null);
        if (parameters.modelName() == null) {
            chatModelBuilder.deploymentName(AzureModelBuilders.DEFAULT_CHAT_MODEL);
        }
        return chatModelBuilder.build();
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
    }

    @Override
    protected String customModelName() {
        return "gpt-4o-2024-11-20"; // requires a deployment with this name
    }

    @Disabled("TODO fix: RateLimit Status code 429")
    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_accept_single_image_as_base64_encoded_string(ChatModel model) {
    }

    @Disabled("TODO fix: RateLimit Status code 429")
    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_accept_multiple_images_as_base64_encoded_strings(ChatModel model) {
    }

    @Override
    @Disabled
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_accept_single_image_as_public_URL(ChatModel model) {
        // TODO fix
    }

    @Override
    @Disabled
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_accept_multiple_images_as_public_URLs(ChatModel model) {
        // TODO fix
    }
}
