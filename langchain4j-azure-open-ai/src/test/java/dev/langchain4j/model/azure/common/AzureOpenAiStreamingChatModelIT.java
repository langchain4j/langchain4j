package dev.langchain4j.model.azure.common;

import static dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities.SupportStatus.NOT_SUPPORTED;

import dev.langchain4j.model.azure.AzureOpenAiStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.common.StreamingChatModelAndCapabilities;
import java.util.List;
import org.junit.jupiter.api.AfterEach;

class AzureOpenAiStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final AzureOpenAiStreamingChatModel AZURE_OPEN_AI_STREAMING_CHAT_MODEL =
            AzureOpenAiStreamingChatModel.builder()
                    .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .deploymentName("gpt-4o-mini")
                    .logRequestsAndResponses(true)
                    .build();

    static final AzureOpenAiStreamingChatModel AZURE_OPEN_AI_STREAMING_CHAT_MODEL_STRICT_SCHEMA =
            AzureOpenAiStreamingChatModel.builder()
                    .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
                    .apiKey(System.getenv("AZURE_OPENAI_KEY"))
                    .deploymentName("gpt-4o-mini")
                    .logRequestsAndResponses(true)
                    .strictJsonSchema(true)
                    .build();

    @Override
    protected List<AbstractChatModelAndCapabilities<StreamingChatLanguageModel>> models() {
        return List.of(
                StreamingChatModelAndCapabilities.builder()
                        .model(AZURE_OPEN_AI_STREAMING_CHAT_MODEL)
                        .mnemonicName("azure open ai chat model")
                        .supportsSingleImageInputAsBase64EncodedString(
                                NOT_SUPPORTED) // Azure OpenAI does not support base64-encoded images
                        .supportsToolChoiceRequiredWithMultipleTools(NOT_SUPPORTED) // TODO implement
                        .supportsDefaultRequestParameters(NOT_SUPPORTED) // TODO implement
                        .supportsModelNameParameter(NOT_SUPPORTED) // TODO implement
                        .supportsMaxOutputTokensParameter(NOT_SUPPORTED) // TODO implement
                        .supportsStopSequencesParameter(NOT_SUPPORTED) // TODO implement
                        .supportsCommonParametersWrappedInIntegrationSpecificClass(NOT_SUPPORTED)
                        .assertExceptionType(false)
                        .assertResponseId(false) // TODO implement
                        .assertResponseModel(false) // TODO implement
                        .assertFinishReason(false) // TODO implement
                        .build(),
                StreamingChatModelAndCapabilities.builder()
                        .model(AZURE_OPEN_AI_STREAMING_CHAT_MODEL_STRICT_SCHEMA)
                        .mnemonicName("azure open ai chat model with strict schema")
                        .supportsSingleImageInputAsBase64EncodedString(
                                NOT_SUPPORTED) // Azure OpenAI does not support base64-encoded images
                        .supportsToolChoiceRequiredWithMultipleTools(NOT_SUPPORTED) // TODO implement
                        .supportsDefaultRequestParameters(NOT_SUPPORTED) // TODO implement
                        .supportsModelNameParameter(NOT_SUPPORTED) // TODO implement
                        .supportsMaxOutputTokensParameter(NOT_SUPPORTED) // TODO implement
                        .supportsStopSequencesParameter(NOT_SUPPORTED) // TODO implement
                        .supportsCommonParametersWrappedInIntegrationSpecificClass(NOT_SUPPORTED)
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

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_AZURE_OPENAI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
