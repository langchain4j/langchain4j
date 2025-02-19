package dev.langchain4j.model.azure.common;

import static dev.langchain4j.model.chat.common.ChatModelCapabilities.SupportStatus.NOT_SUPPORTED;

import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT2;
import dev.langchain4j.model.chat.common.ChatLanguageModelCapabilities;
import dev.langchain4j.model.chat.common.ChatModelCapabilities;
import java.util.List;

class AzureOpenAiChatModelIT extends AbstractChatModelIT2 {

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
    protected List<ChatModelCapabilities<ChatLanguageModel>> models() {
        return List.of(
                ChatLanguageModelCapabilities.builder()
                        .model(AZURE_OPEN_AI_CHAT_MODEL)
                        .mnemonicName("azure open ai chat model")
                        // .supportsSingleImageInputAsPublicURL(false)
                        .supportsSingleImageInputAsBase64EncodedString(NOT_SUPPORTED)
                        .supportsToolChoiceRequired(NOT_SUPPORTED)
                        .supportsDefaultRequestParameters(NOT_SUPPORTED)
                        .supportsModelNameParameter(NOT_SUPPORTED)
                        .supportsMaxOutputTokensParameter(NOT_SUPPORTED)
                        .supportsStopSequencesParameter(NOT_SUPPORTED)
                        .supportsToolChoiceRequired(NOT_SUPPORTED)
                        .assertExceptionType(false)
                        .assertResponseId(false)
                        .assertResponseModel(false)
                        .assertFinishReason(false)
                        .build(),
                ChatLanguageModelCapabilities.builder()
                        .model(AZURE_OPEN_AI_CHAT_MODEL_STRICT_SCHEMA)
                        .mnemonicName("azure open ai chat model with strict schema")
                        // .supportsSingleImageInputAsPublicURL(false)
                        .supportsSingleImageInputAsBase64EncodedString(NOT_SUPPORTED)
                        .supportsToolChoiceRequired(NOT_SUPPORTED)
                        .supportsDefaultRequestParameters(NOT_SUPPORTED)
                        .supportsModelNameParameter(NOT_SUPPORTED)
                        .supportsMaxOutputTokensParameter(NOT_SUPPORTED)
                        .supportsStopSequencesParameter(NOT_SUPPORTED)
                        .supportsToolChoiceRequired(NOT_SUPPORTED)
                        .assertExceptionType(false)
                        .assertResponseId(false)
                        .assertResponseModel(false)
                        .assertFinishReason(false)
                        .build());
    }

    @Override
    protected boolean supportsModelNameParameter() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsMaxOutputTokensParameter() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsStopSequencesParameter() {
        return false; // TODO implement
    }

    @Override
    protected boolean assertResponseId() {
        return false; // TODO implement
    }

    @Override
    protected boolean assertResponseModel() {
        return false; // TODO implement
    }

    protected boolean assertFinishReason() {
        return false; // TODO implement
    }
}
