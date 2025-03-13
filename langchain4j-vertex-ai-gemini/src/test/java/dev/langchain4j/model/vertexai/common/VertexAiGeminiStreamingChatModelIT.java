package dev.langchain4j.model.vertexai.common;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.common.StreamingChatModelAndCapabilities;
import dev.langchain4j.model.vertexai.VertexAiGeminiStreamingChatModel;
import org.junit.jupiter.api.AfterEach;

import java.util.List;

import static dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities.SupportStatus.NOT_SUPPORTED;

class VertexAiGeminiStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final VertexAiGeminiStreamingChatModel VERTEX_AI_GEMINI_STREAMING_CHAT_MODEL =
            VertexAiGeminiStreamingChatModel.builder()
                    .project(System.getenv("GCP_PROJECT_ID"))
                    .location(System.getenv("GCP_LOCATION"))
                    .modelName("gemini-1.5-flash")
                    .build();

    @Override
    protected List<AbstractChatModelAndCapabilities<StreamingChatLanguageModel>> models() {
        return List.of(
                StreamingChatModelAndCapabilities.builder()
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
    protected boolean disableParametersInDefaultModelTests() {
        return true; // TODO implement
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_VERTEX_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
