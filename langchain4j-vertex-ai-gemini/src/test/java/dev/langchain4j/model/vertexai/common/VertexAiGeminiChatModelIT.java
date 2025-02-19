package dev.langchain4j.model.vertexai.common;

import static dev.langchain4j.model.chat.common.ChatModelCapabilities.SupportStatus.NOT_SUPPORTED;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT2;
import dev.langchain4j.model.chat.common.ChatLanguageModelCapabilities;
import dev.langchain4j.model.chat.common.ChatModelCapabilities;
import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel;
import java.util.List;
import org.junit.jupiter.api.AfterEach;

class VertexAiGeminiChatModelIT extends AbstractChatModelIT2 {

    // TODO https://github.com/langchain4j/langchain4j/issues/2219
    // TODO https://github.com/langchain4j/langchain4j/issues/2220

    static final ChatModelCapabilities<ChatLanguageModel> VERTEX_AI_GEMINI_CHAT_MODEL =
            ChatLanguageModelCapabilities.builder()
                    .model(VertexAiGeminiChatModel.builder()
                            .project(System.getenv("GCP_PROJECT_ID"))
                            .location(System.getenv("GCP_LOCATION"))
                            .modelName("gemini-1.5-flash")
                            .build())
                    .mnemonicName("vertex ai gemini chat model")
                    .supportsMaxOutputTokensParameter(NOT_SUPPORTED)
                    .supportsModelNameParameter(NOT_SUPPORTED)
                    .supportsStopSequencesParameter(NOT_SUPPORTED)
                    .supportsToolChoiceRequired(NOT_SUPPORTED)
                    .supportsCommonParametersWrappedInIntegrationSpecificClass(NOT_SUPPORTED)
                    .supportsJsonResponseFormat(NOT_SUPPORTED)
                    .supportsJsonResponseFormatWithSchema(NOT_SUPPORTED)
                    .supportsToolsAndJsonResponseFormatWithSchema(NOT_SUPPORTED)
                    .assertExceptionType(false)
                    .assertResponseId(false)
                    .assertFinishReason(false)
                    .assertResponseModel(false)
                    .build();

    @Override
    protected List<ChatModelCapabilities<ChatLanguageModel>> models() {
        return List.of(
                VERTEX_AI_GEMINI_CHAT_MODEL
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

    protected boolean assertFinishReason() {
        return false; // TODO implement
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        String ciDelaySeconds = System.getenv("CI_DELAY_SECONDS_VERTEX_AI_GEMINI");
        if (ciDelaySeconds != null) {
            Thread.sleep(Integer.parseInt(ciDelaySeconds) * 1000L);
        }
    }
}
