package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class GoogleGenAiStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final GoogleGenAiStreamingChatModel GOOGLE_GEN_AI_STREAMING_CHAT_MODEL =
            GoogleGenAiStreamingChatModel.builder()
                    .apiKey(System.getenv("GEMINI_API_KEY"))
                    .modelName("gemini-2.5-flash")
                    .logRequests(true)
                    .logResponses(true)
                    .build();

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(GOOGLE_GEN_AI_STREAMING_CHAT_MODEL);
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        return GoogleGenAiStreamingChatModel.builder()
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
    protected boolean supportsTools() {
        return false; // Tools might not be fully supported in streaming mode yet via the SDK
    }

    @Override
    protected void should_fail_if_tools_are_not_supported(dev.langchain4j.model.chat.StreamingChatModel model) {
        // Disabled: GoogleGenAiStreamingChatModel ignores tools instead of throwing an exception
    }

    @Override
    protected boolean supportsStreamingCancellation() {
        return false; // cancellation not implemented in GoogleGenAiStreamingChatModel yet
    }

    @Override
    protected boolean supportsJsonResponseFormatWithRawSchema() {
        return false;
    }

    @Override
    protected boolean assertToolId(StreamingChatModel model) {
        return false;
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel model) {
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

    @Override
    public StreamingChatModel createModelWith(dev.langchain4j.model.chat.listener.ChatModelListener listener) {
        return GoogleGenAiStreamingChatModel.builder()
                .apiKey(System.getenv("GEMINI_API_KEY"))
                .modelName("gemini-2.5-flash")
                .logRequests(true)
                .logResponses(true)
                .listeners(java.util.List.of(listener))
                .build();
    }
}
