package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GoogleGenAiChatModelIT extends AbstractChatModelIT {

    static final GoogleGenAiChatModel GOOGLE_GEN_AI_CHAT_MODEL = GoogleGenAiChatModel.builder()
            .apiKey(System.getenv("GEMINI_API_KEY"))
            .modelName("gemini-2.5-flash")
            .logRequests(true)
            .logResponses(true)
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(GOOGLE_GEN_AI_CHAT_MODEL);
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        return GoogleGenAiChatModel.builder()
                .apiKey(System.getenv("GEMINI_API_KEY"))
                .defaultRequestParameters(parameters)
                .modelName(getOrDefault(parameters.modelName(), "gemini-2.5-flash"))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    protected String customModelName() {
        return "gemini-2.5-pro";
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return ChatRequestParameters.builder().maxOutputTokens(maxOutputTokens).build();
    }

    @Override
    protected boolean supportsToolsAndJsonResponseFormatWithSchema() {
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormatWithRawSchema() {
        return false;
    }

    @Override
    protected boolean assertToolId(ChatModel model) {
        return false;
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(ChatModel model) {
        return GoogleGenAiChatResponseMetadata.class;
    }

    @Override
    protected boolean assertResponseId() {
        return false;
    }

    @Override
    protected boolean assertFinishReason() {
        return false;
    }

    @Override
    protected void assertOutputTokenCount(dev.langchain4j.model.output.TokenUsage tokenUsage, Integer maxOutputTokens) {
        org.assertj.core.api.Assertions.assertThat(tokenUsage.outputTokenCount())
                .isLessThanOrEqualTo(maxOutputTokens);
    }
}
