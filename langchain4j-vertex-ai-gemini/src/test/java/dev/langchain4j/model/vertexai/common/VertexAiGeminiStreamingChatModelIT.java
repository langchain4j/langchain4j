package dev.langchain4j.model.vertexai.common;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT2;
import dev.langchain4j.model.chat.common.ChatModelCapabilities;
import dev.langchain4j.model.chat.common.StreamingChatLanguageModelCapabilities;
import dev.langchain4j.model.vertexai.VertexAiGeminiStreamingChatModel;
import org.junit.jupiter.api.AfterEach;

import java.util.List;

import static dev.langchain4j.model.chat.common.ChatModelCapabilities.SupportStatus.NOT_SUPPORTED;

class VertexAiGeminiStreamingChatModelIT extends AbstractStreamingChatModelIT2 {

    static final VertexAiGeminiStreamingChatModel VERTEX_AI_GEMINI_STREAMING_CHAT_MODEL =
            VertexAiGeminiStreamingChatModel.builder()
                    .project(System.getenv("GCP_PROJECT_ID"))
                    .location(System.getenv("GCP_LOCATION"))
                    .modelName("gemini-1.5-flash")
                    .build();

    @Override
    protected List<ChatModelCapabilities<StreamingChatLanguageModel>> models() {
        return List.of(
                StreamingChatLanguageModelCapabilities.builder()
                        .model(VERTEX_AI_GEMINI_STREAMING_CHAT_MODEL)
                        .mnemonicName("vertex ai gemini chat model")
                        .supportsMaxOutputTokensParameter(NOT_SUPPORTED) // TODO implement
                        .supportsModelNameParameter(NOT_SUPPORTED) // TODO implement
                        .supportsStopSequencesParameter(NOT_SUPPORTED) // TODO implement
                        .supportsToolChoiceRequired(NOT_SUPPORTED) // TODO implement
                        .supportsCommonParametersWrappedInIntegrationSpecificClass(NOT_SUPPORTED)
                        .supportsJsonResponseFormat(NOT_SUPPORTED) // TODO implement
                        .supportsJsonResponseFormatWithSchema(NOT_SUPPORTED)
                        .supportsToolsAndJsonResponseFormatWithSchema(NOT_SUPPORTED)
                        .assertExceptionType(false)
                        .assertResponseId(false) // TODO implement
                        .assertFinishReason(false) // TODO implement
                        .assertResponseModel(false) // TODO implement
                        .assertThreads(false) // TODO what to do with it?
                        .build()
                // TODO add more model configs, see OpenAiChatModelIT
        );
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

    @Override
    protected boolean assertFinishReason() {
        return false; // TODO implement
    }

    @Override
    protected boolean assertThreads() {
        return false; // TODO what to do with it?
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_VERTEX_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
