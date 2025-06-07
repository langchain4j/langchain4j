package dev.langchain4j.model.azure.common;

import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.azure.AzureOpenAiChatRequestParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AzureOpenAiChatModelIT extends AbstractChatModelIT {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219

    static final AzureOpenAiChatModel AZURE_OPEN_AI_CHAT_MODEL = AzureOpenAiChatModel.builder()
            .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .apiKey(System.getenv("AZURE_OPENAI_KEY"))
            .deploymentName("gpt-4o-mini")
            .logRequestsAndResponses(false) // images are huge in logs
            .build();

    static final AzureOpenAiChatModel AZURE_OPEN_AI_CHAT_MODEL_STRICT_SCHEMA = AzureOpenAiChatModel.builder()
            .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .apiKey(System.getenv("AZURE_OPENAI_KEY"))
            .deploymentName("gpt-4o-mini")
            .strictJsonSchema(true)
            .logRequestsAndResponses(false) // images are huge in logs
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(AZURE_OPEN_AI_CHAT_MODEL, AZURE_OPEN_AI_CHAT_MODEL_STRICT_SCHEMA);
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

    @Override
    protected boolean supportsToolChoiceRequiredWithMultipleTools() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsSingleImageInputAsBase64EncodedString() {
        return false; // Azure OpenAI does not support base64-encoded images
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        AzureOpenAiChatModel.Builder azureOpenAiChatModelBuilder = AzureOpenAiChatModel.builder()
                .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                .defaultRequestParameters(parameters)
                .logRequestsAndResponses(true);
        if (parameters.modelName() == null) {
            azureOpenAiChatModelBuilder.deploymentName("gpt-4o-mini");
        }
        return azureOpenAiChatModelBuilder.build();
    }

    @Override
    protected String customModelName() {
        return "gpt-4-turbo-2024-04-09";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return AzureOpenAiChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected boolean assertResponseId() {
        return false; // TODO implement
    }

    @Override
    protected boolean assertResponseModel() {
        return false; // TODO implement
    }

    @Override
    protected boolean assertFinishReason() {
        return false; // TODO implement
    }
}
