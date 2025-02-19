package dev.langchain4j.model.azure.common;

import static dev.langchain4j.model.chat.common.ChatModelCapabilities.Capability.FAIL;

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
                        // .supportsSingleImageInputAsPublicURL(false)
                        .supportsSingleImageInputAsBase64EncodedString(FAIL)
                        .supportsToolChoiceRequired(FAIL)
                        .supportsDefaultRequestParameters(FAIL)
                        .supportsModelNameParameter(FAIL)
                        .supportsMaxOutputTokensParameter(FAIL)
                        .supportsStopSequencesParameter(FAIL)
                        .supportsToolChoiceRequired(FAIL)
                        .assertExceptionType(false)
                        .assertResponseId(false)
                        .assertResponseModel(false)
                        .assertFinishReason(false)
                        .build(),
                ChatLanguageModelCapabilities.builder()
                        .model(AZURE_OPEN_AI_CHAT_MODEL_STRICT_SCHEMA)
                        // .supportsSingleImageInputAsPublicURL(false)
                        .supportsSingleImageInputAsBase64EncodedString(FAIL)
                        .supportsToolChoiceRequired(FAIL)
                        .supportsDefaultRequestParameters(FAIL)
                        .supportsModelNameParameter(FAIL)
                        .supportsMaxOutputTokensParameter(FAIL)
                        .supportsStopSequencesParameter(FAIL)
                        .supportsToolChoiceRequired(FAIL)
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
