package dev.langchain4j.model.openaiofficial.common;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Set;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModelIT.API_VERSION;
import static dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModelIT.MODEL_NAME;

class OpenAiOfficialChatModelIT extends AbstractChatModelIT {

    static final OpenAiOfficialChatModel OPEN_AI_CHAT_MODEL = OpenAiOfficialChatModel.builder()
            .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
            .azureDeploymentName(MODEL_NAME.toString())
            .azureOpenAIServiceVersion(API_VERSION)
            .modelName(MODEL_NAME)
            .build();

    static final OpenAiOfficialChatModel OPEN_AI_CHAT_MODEL_STRICT_SCHEMA = OpenAiOfficialChatModel.builder()
            .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
            .azureDeploymentName(MODEL_NAME.toString())
            .azureOpenAIServiceVersion(API_VERSION)
            .modelName(MODEL_NAME)
            .supportedCapabilities(Set.of(RESPONSE_FORMAT_JSON_SCHEMA))
            .strictJsonSchema(true)
            .build();

    @Override
    protected List<ChatLanguageModel> models() {
        return List.of(
                OPEN_AI_CHAT_MODEL,
                OPEN_AI_CHAT_MODEL_STRICT_SCHEMA
        );
    }

    @Override
    protected ChatLanguageModel createModelWith(ChatRequestParameters parameters) {
        OpenAiOfficialChatModel.OpenAiOfficialChatModelBuilder openAiChatModelBuilder = OpenAiOfficialChatModel.builder()
                .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
                .azureApiKey(System.getenv("AZURE_OPENAI_KEY"))
                .azureDeploymentName(MODEL_NAME.toString())
                .azureOpenAIServiceVersion(API_VERSION)
                .modelName(MODEL_NAME)
                .defaultRequestParameters(parameters);
        if (parameters.modelName() == null) {
            openAiChatModelBuilder.modelName(MODEL_NAME);
        }
        return openAiChatModelBuilder.build();
    }

    @Override
    @Disabled
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_accept_single_image_as_public_URL(ChatLanguageModel model) {
        // TODO fix
    }

    @Override
    @Disabled
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_accept_multiple_images_as_public_URLs(ChatLanguageModel model) {
        // TODO fix
    }

    @Override
    protected boolean supportsDefaultRequestParameters() {
        return true; // TODO implement
    }

    @Override
    protected boolean supportsModelNameParameter() {
        return true; // TODO implement
    }

    @Override
    protected boolean supportsMaxOutputTokensParameter() {
        return true; // TODO implement
    }

    @Override
    protected boolean supportsStopSequencesParameter() {
        return true; // TODO implement
    }

    @Override
    protected boolean supportsToolChoiceRequired() {
        return true; // TODO implement
    }

    @Override
    protected boolean supportsSingleImageInputAsBase64EncodedString() {
        return false; // Azure OpenAI does not support base64-encoded images
    }

    @Override
    protected boolean assertResponseId() {
        return true; // TODO implement
    }

    @Override
    protected boolean assertResponseModel() {
        return true; // TODO implement
    }

    protected boolean assertFinishReason() {
        return true; // TODO implement
    }
}
