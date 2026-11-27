package dev.langchain4j.model.azure.common;

import dev.langchain4j.model.azure.AzureModelBuilders;
import dev.langchain4j.model.azure.AzureOpenAiResponsesChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiResponsesChatResponseMetadata;
import dev.langchain4j.model.openai.OpenAiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "AZURE_OPENAI_KEY", matches = ".+")
class AzureOpenAiResponsesChatModelIT extends AbstractChatModelIT {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219

    private static final int MAX_OUTPUT_TOKENS_MIN_VALUE = 16;

    static final AzureOpenAiResponsesChatModel AZURE_OPEN_AI_RESPONSES_CHAT_MODEL =
            AzureModelBuilders.responsesChatModelBuilder()
                    .logRequestsAndResponses(false) // images are huge in logs
                    .build();

    static final AzureOpenAiResponsesChatModel AZURE_OPEN_AI_CHAT_MODEL_STRICT_SCHEMA =
            AzureModelBuilders.responsesChatModelBuilder()
                    .strictJsonSchema(true)
                    .logRequestsAndResponses(false) // images are huge in logs
                    .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(AZURE_OPEN_AI_RESPONSES_CHAT_MODEL, AZURE_OPEN_AI_CHAT_MODEL_STRICT_SCHEMA);
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        AzureOpenAiResponsesChatModel.Builder chatModelBuilder = AzureModelBuilders.responsesChatModelBuilder()
                .defaultRequestParameters(parameters)
                .deploymentName(null)
                .maxOutputTokens(null);
        if (parameters.modelName() == null) {
            chatModelBuilder.deploymentName(AzureModelBuilders.DEFAULT_RESPONSES_CHAT_MODEL);
        }
        return chatModelBuilder.build();
    }

    @Override
    protected boolean supportsStopSequencesParameter() {
        return false;
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(ChatModel model) {
        return OpenAiResponsesChatResponseMetadata.class;
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(ChatModel model) {
        return OpenAiTokenUsage.class;
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
    }

    @Override
    protected String customModelName() {
        return "gpt-6-luna-2026-11-10"; // requires a deployment with this name
    }

    @Override
    protected int maxOutputTokens() {
        return MAX_OUTPUT_TOKENS_MIN_VALUE;
    }

    @Disabled("TODO fix: RateLimit Status code 429")
    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_accept_single_image_as_base64_encoded_string(ChatModel model) {}

    @Disabled("TODO fix: RateLimit Status code 429")
    @Override
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_accept_multiple_images_as_base64_encoded_strings(ChatModel model) {}

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

    @Override
    protected ChatRequestParameters saveTokens(ChatRequestParameters parameters) {
        return parameters; // Azure OpenAI does not support max_tokens any more, only max_completion_tokens
    }

    @Override
    @Disabled("Setting 'max_tokens' and 'max_completion_tokens' at the same time is not supported.")
    protected void should_respect_common_parameters_wrapped_in_integration_specific_class_in_chat_request(
            ChatModel model) {}

    @Override
    @Disabled("Setting 'max_tokens' and 'max_completion_tokens' at the same time is not supported.")
    protected void
            should_respect_common_parameters_wrapped_in_integration_specific_class_in_default_model_parameters() {}

    @Override
    @Disabled("Setting 'max_tokens' and 'max_completion_tokens' at the same time is not supported.")
    protected void should_respect_maxOutputTokens_in_chat_request(ChatModel model) {}

    @Override
    @Disabled("Setting 'max_tokens' and 'max_completion_tokens' at the same time is not supported.")
    protected void should_respect_maxOutputTokens_in_default_model_parameters() {}
}
