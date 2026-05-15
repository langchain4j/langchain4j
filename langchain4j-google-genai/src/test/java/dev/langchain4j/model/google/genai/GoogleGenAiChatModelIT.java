package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiChatModelIT extends AbstractChatModelIT {

    static final GoogleGenAiChatModel GOOGLE_GEN_AI_CHAT_MODEL = GoogleGenAiChatModel.builder()
            .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
            .modelName("gemini-2.5-flash")
            .build();

    @Override
    protected List<ChatModel> models() {
        return List.of(GOOGLE_GEN_AI_CHAT_MODEL);
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        return GoogleGenAiChatModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .defaultRequestParameters(parameters)
                .modelName(getOrDefault(parameters.modelName(), "gemini-2.5-flash"))
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
        return false; // TODO
    }

    @Override
    protected boolean supportsJsonResponseFormatWithRawSchema() {
        return false; // TODO
    }

    @Override
    protected boolean assertToolId(ChatModel model) {
        return false; // TODO
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(ChatModel model) {
        return GoogleGenAiChatResponseMetadata.class;
    }

    @Override
    protected boolean assertResponseId() {
        return false; // TODO
    }

    @Override
    protected boolean assertFinishReason() {
        return false; // TODO
    }

    @Override
    protected void assertOutputTokenCount(TokenUsage tokenUsage, Integer maxOutputTokens) {
        assertThat(tokenUsage.outputTokenCount()).isLessThanOrEqualTo(maxOutputTokens); // TODO
    }
}
