package dev.langchain4j.model.azure.common;

import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.common.ChatModelAndCapabilities;

import java.util.List;

import static dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities.SupportStatus.NOT_SUPPORTED;

class AzureOpenAiChatModelIT extends AbstractChatModelIT {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219

    static final AzureOpenAiChatModel AZURE_OPEN_AI_CHAT_MODEL = AzureOpenAiChatModel.builder()
            .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .apiKey(System.getenv("AZURE_OPENAI_KEY"))
            .deploymentName("gpt-4o-mini")
            .logRequestsAndResponses(true)
            .build();

    static final AzureOpenAiChatModel AZURE_OPEN_AI_CHAT_MODEL_STRICT_SCHEMA = AzureOpenAiChatModel.builder()
            .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .apiKey(System.getenv("AZURE_OPENAI_KEY"))
            .deploymentName("gpt-4o-mini")
            .strictJsonSchema(true)
            .logRequestsAndResponses(true)
            .build();

    @Override
    protected List<AbstractChatModelAndCapabilities<ChatLanguageModel>> models() {
        return List.of(
                ChatModelAndCapabilities.builder()
                        .model(AZURE_OPEN_AI_CHAT_MODEL)
                        .mnemonicName("azure open ai chat model")
                        .supportsSingleImageInputAsBase64EncodedString(
                                NOT_SUPPORTED) // Azure OpenAI does not support base64-encoded images
                        .supportsToolChoiceRequiredWithMultipleTools(NOT_SUPPORTED)
                        .supportsDefaultRequestParameters(NOT_SUPPORTED)
                        .supportsModelNameParameter(NOT_SUPPORTED)
                        .supportsMaxOutputTokensParameter(NOT_SUPPORTED)
                        .supportsStopSequencesParameter(NOT_SUPPORTED)
                        .assertExceptionType(false)
                        .assertResponseId(false)
                        .assertResponseModel(false)
                        .assertFinishReason(false)
                        .build(),
                ChatModelAndCapabilities.builder()
                        .model(AZURE_OPEN_AI_CHAT_MODEL_STRICT_SCHEMA)
                        .mnemonicName("azure open ai chat model with strict schema")
                        .supportsSingleImageInputAsBase64EncodedString(
                                NOT_SUPPORTED) // Azure OpenAI does not support base64-encoded images
                        .supportsToolChoiceRequiredWithMultipleTools(NOT_SUPPORTED)
                        .supportsDefaultRequestParameters(NOT_SUPPORTED)
                        .supportsModelNameParameter(NOT_SUPPORTED)
                        .supportsMaxOutputTokensParameter(NOT_SUPPORTED)
                        .supportsStopSequencesParameter(NOT_SUPPORTED)
                        .assertExceptionType(false)
                        .assertResponseId(false)
                        .assertResponseModel(false)
                        .assertFinishReason(false)
                        .build());
    }

    @Override
    protected boolean disableParametersInDefaultModelTests() {
        return true; // TODO implement
    }
}
