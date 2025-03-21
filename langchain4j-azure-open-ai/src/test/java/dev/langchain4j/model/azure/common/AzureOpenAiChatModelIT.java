package dev.langchain4j.model.azure.common;

import static dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities.SupportStatus.NOT_SUPPORTED;

import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.common.ChatModelAndCapabilities;
import java.util.List;

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
                        .supportsToolChoiceRequiredWithMultipleTools(NOT_SUPPORTED) // TODO implement
                        .supportsDefaultRequestParameters(NOT_SUPPORTED) // TODO implement
                        .supportsModelNameParameter(NOT_SUPPORTED) // TODO implement
                        .supportsMaxOutputTokensParameter(NOT_SUPPORTED) // TODO implement
                        .supportsStopSequencesParameter(NOT_SUPPORTED) // TODO implement
                        .assertExceptionType(false)
                        .assertResponseId(false) // TODO implement
                        .assertResponseModel(false) // TODO implement
                        .assertFinishReason(false) // TODO implement
                        .build(),
                ChatModelAndCapabilities.builder()
                        .model(AZURE_OPEN_AI_CHAT_MODEL_STRICT_SCHEMA)
                        .mnemonicName("azure open ai chat model with strict schema")
                        .supportsSingleImageInputAsBase64EncodedString(
                                NOT_SUPPORTED) // Azure OpenAI does not support base64-encoded images
                        .supportsToolChoiceRequiredWithMultipleTools(NOT_SUPPORTED) // TODO implement
                        .supportsDefaultRequestParameters(NOT_SUPPORTED) // TODO implement
                        .supportsModelNameParameter(NOT_SUPPORTED) // TODO implement
                        .supportsMaxOutputTokensParameter(NOT_SUPPORTED) // TODO implement
                        .supportsStopSequencesParameter(NOT_SUPPORTED) // TODO implement
                        .assertExceptionType(false)
                        .assertResponseId(false) // TODO implement
                        .assertResponseModel(false) // TODO implement
                        .assertFinishReason(false) // TODO implement
                        .build());
    }

    @Override
    protected boolean disableParametersInDefaultModelTests() {
        return true; // TODO implement
    }
}
