package dev.langchain4j.model.vertexai.common;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.common.ChatModelAndCapabilities;
import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel;
import org.junit.jupiter.api.AfterEach;

import java.util.List;

import static dev.langchain4j.model.chat.common.AbstractChatModelAndCapabilities.SupportStatus.NOT_SUPPORTED;

class VertexAiGeminiChatModelIT extends AbstractChatModelIT {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219
    // TODO https://github.com/langchain4j/langchain4j/issues/2220

    static final AbstractChatModelAndCapabilities<ChatLanguageModel> VERTEX_AI_GEMINI_CHAT_MODEL =
            ChatModelAndCapabilities.builder()
                    .model(VertexAiGeminiChatModel.builder()
                            .project(System.getenv("GCP_PROJECT_ID"))
                            .location(System.getenv("GCP_LOCATION"))
                            .modelName("gemini-1.5-flash")
                            .build())
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
                    .build();

    @Override
    protected List<AbstractChatModelAndCapabilities<ChatLanguageModel>> models() {
        return List.of(
                VERTEX_AI_GEMINI_CHAT_MODEL
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
