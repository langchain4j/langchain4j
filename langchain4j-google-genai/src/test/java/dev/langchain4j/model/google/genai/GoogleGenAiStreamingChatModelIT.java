package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final GoogleGenAiStreamingChatModel GOOGLE_GEN_AI_STREAMING_CHAT_MODEL =
            GoogleGenAiStreamingChatModel.builder()
                    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                    .modelName("gemini-2.5-flash")
                    .build();

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(GOOGLE_GEN_AI_STREAMING_CHAT_MODEL);
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        return GoogleGenAiStreamingChatModel.builder()
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
    protected boolean supportsStreamingCancellation() {
        return false; // TODO
    }

    @Override
    protected boolean supportsJsonResponseFormatWithRawSchema() {
        return false; // TODO
    }

    @Override
    protected boolean assertToolId(StreamingChatModel model) {
        return false; // TODO
    }

    @Override
    protected boolean supportsPartialToolStreaming(StreamingChatModel model) {
        return false; // TODO
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id) {
        io.verify(handler, atLeast(0)).onPartialResponse(any(), any());
        io.verify(handler).onCompleteToolCall(complete(0, id, "getWeather", "{\"city\":\"Munich\"}"));
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder io, String id1, String id2) {
        verifyToolCallbacks(handler, io, id1);
        io.verify(handler, atLeast(0)).onPartialResponse(any(), any());
        io.verify(handler).onCompleteToolCall(complete(1, id2, "getTime", "{\"country\":\"France\"}"));
    }

    @Override
    protected Class<? extends ChatResponseMetadata> chatResponseMetadataType(StreamingChatModel model) {
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

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return GoogleGenAiStreamingChatModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .modelName("gemini-2.5-flash")
                .listeners(List.of(listener))
                .build();
    }
}
