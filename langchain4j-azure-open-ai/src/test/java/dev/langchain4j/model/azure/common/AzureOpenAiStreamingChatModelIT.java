package dev.langchain4j.model.azure.common;

import dev.langchain4j.model.azure.AzureOpenAiStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static java.time.Duration.ofSeconds;

class AzureOpenAiStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final AzureOpenAiStreamingChatModel AZURE_OPEN_AI_STREAMING_CHAT_MODEL = AzureOpenAiStreamingChatModel.builder()
            .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .apiKey(System.getenv("AZURE_OPENAI_KEY"))
            .deploymentName("gpt-4o-mini")
            .logRequestsAndResponses(false) // images are huge in logs
            .timeout(ofSeconds(120))
            .build();

    static final AzureOpenAiStreamingChatModel AZURE_OPEN_AI_STREAMING_CHAT_MODEL_STRICT_SCHEMA = AzureOpenAiStreamingChatModel.builder()
            .endpoint(System.getenv("AZURE_OPENAI_ENDPOINT"))
            .apiKey(System.getenv("AZURE_OPENAI_KEY"))
            .deploymentName("gpt-4o-mini")
            .logRequestsAndResponses(false) // images are huge in logs
            .strictJsonSchema(true)
            .build();

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(
                AZURE_OPEN_AI_STREAMING_CHAT_MODEL,
                AZURE_OPEN_AI_STREAMING_CHAT_MODEL_STRICT_SCHEMA
        );
    }

    @Override
    @Disabled
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_accept_single_image_as_public_URL(StreamingChatModel model) {
        // TODO fix
    }

    @Override
    @Disabled
    @ParameterizedTest
    @MethodSource("modelsSupportingImageInputs")
    protected void should_accept_multiple_images_as_public_URLs(StreamingChatModel model) {
        // TODO fix
    }

    @Override
    protected boolean supportsDefaultRequestParameters() {
        return false; // TODO implement
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
    protected boolean supportsToolChoiceRequiredWithMultipleTools() {
        return false; // TODO implement
    }

    @Override
    protected boolean supportsSingleImageInputAsBase64EncodedString() {
        return false; // Azure OpenAI does not support base64-encoded images
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

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_AZURE_OPENAI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
