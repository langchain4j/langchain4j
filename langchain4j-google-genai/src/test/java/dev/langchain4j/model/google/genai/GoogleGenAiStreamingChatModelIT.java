package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "GOOGLE_AI_GEMINI_API_KEY", matches = ".+")
class GoogleGenAiStreamingChatModelIT extends AbstractStreamingChatModelIT {

    static final GoogleGenAiStreamingChatModel GOOGLE_GEN_AI_STREAMING_CHAT_MODEL =
            GoogleGenAiStreamingChatModel.builder()
                    .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                    .modelName("gemini-3.8-flash")
                    .thinkingBudget(0)
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
                .modelName(getOrDefault(parameters.modelName(), "gemini-3.8-flash"))
                .thinkingBudget(0)
                .build();
    }

    @Override
    protected String customModelName() {
        return "gemini-3.1-flash-lite";
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
    protected boolean assertTimesOnPartialResponseWasCalled() {
        return false; // after a tool call, Gemini often streams the final (short) answer in a single chunk
    }

    @Disabled("Gemini cannot do it reliably")
    @Override
    protected void should_execute_multiple_tools_in_parallel_then_answer(StreamingChatModel model) {}

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
    protected void assertTotalTokenCount(TokenUsage tokenUsage) {
        // total token count can be more than input+output due to thinking/reasoning
        assertThat(tokenUsage.totalTokenCount())
                .isGreaterThanOrEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        return GoogleGenAiStreamingChatModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .modelName("gemini-3.8-flash")
                .listeners(List.of(listener))
                .build();
    }

    @Test
    void should_apply_generate_content_config_customizer() {
        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .modelName("gemini-3.8-flash")
                .generateContentConfigCustomizer(
                        config -> config.temperature(0.7f).seed(42))
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("Respond with exactly one word: Hello.", handler);

        assertThat(handler.get().aiMessage().text()).isNotBlank();
    }

    @Test
    void should_stream_code_run_with_code_execution_tool() {
        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .apiKey(System.getenv("GOOGLE_AI_GEMINI_API_KEY"))
                .modelName("gemini-3.8-flash")
                .enableCodeExecution(true)
                .includeCodeExecutionOutput(true)
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat("What is 2 to the power of 100? Generate and run code to compute it.", handler);

        assertThat(handler.get().aiMessage().text())
                .contains("Code executed:\n```python\n")
                .containsPattern("Output:\\n```\\n[^`]*1267650600228229401496703205376");
    }

    @Override
    protected Class<? extends TokenUsage> tokenUsageType(StreamingChatModel model) {
        return GoogleGenAiTokenUsage.class;
    }
}
