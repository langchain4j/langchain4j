package dev.langchain4j.model.jitllm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.InOrder;

@EnabledIfEnvironmentVariable(named = "MODEL", matches = ".+")
class JitLLMStreamingChatModelIT extends AbstractStreamingChatModelIT {

    private JitLLMStreamingChatModel model;
    private final List<JitLLMStreamingChatModel> modelsCreatedByTests = new ArrayList<>();

    private static JitLLMStreamingChatModel.JitLLMStreamingChatModelBuilder modelBuilder() {
        return JitLLMStreamingChatModel.builder()
                .modelPath(TestModelPath.fromEnvironment())
                .temperature(0.6)
                .topP(0.95)
                .maxTokens(2048)
                .seed(12345)
                .think(false);
    }

    @BeforeAll
    void loadModel() {
        model = modelBuilder().build();
    }

    @AfterEach
    void closeModelsCreatedByTests() {
        modelsCreatedByTests.forEach(JitLLMStreamingChatModel::close);
        modelsCreatedByTests.clear();
    }

    @AfterAll
    void closeModel() {
        if (model != null) {
            model.close();
        }
    }

    @Override
    protected List<StreamingChatModel> models() {
        return List.of(SharedModelView.of(model));
    }

    @Override
    protected StreamingChatModel createModelWith(ChatRequestParameters parameters) {
        JitLLMStreamingChatModel created = JitLLMStreamingChatModel.builder()
                .modelPath(TestModelPath.fromEnvironment())
                .think(false)
                .defaultRequestParameters(parameters)
                .build();
        modelsCreatedByTests.add(created);
        return created;
    }

    @Override
    public StreamingChatModel createModelWith(ChatModelListener listener) {
        JitLLMStreamingChatModel created =
                modelBuilder().listeners(List.of(listener)).build();
        modelsCreatedByTests.add(created);
        return created;
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return DefaultChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder inOrder, String id) {
        inOrder.verify(handler).onCompleteToolCall(complete(0, id, "getWeather", "{\"city\":\"Munich\"}"));
    }

    @Override
    protected void verifyToolCallbacks(StreamingChatResponseHandler handler, InOrder inOrder, String id1, String id2) {
        verifyToolCallbacks(handler, inOrder, id1);
        inOrder.verify(handler).onCompleteToolCall(complete(1, id2, "getTime", "{\"country\":\"France\"}"));
    }

    @Override
    protected void assertOutputTokenCount(TokenUsage tokenUsage, Integer maxOutputTokens) {
        // jitLLM 1.0.2 stops one token before maxNewTokens
        assertThat(tokenUsage.outputTokenCount()).isBetween(maxOutputTokens - 1, maxOutputTokens);
    }

    @Override
    protected boolean supportsModelNameParameter() {
        return false;
    }

    @Override
    protected boolean supportsToolChoiceRequired() {
        return false;
    }

    @Override
    protected boolean supportsPartialToolStreaming(StreamingChatModel model) {
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormat() {
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormatWithSchema() {
        return false;
    }

    @Override
    protected boolean supportsJsonResponseFormatWithRawSchema() {
        return false;
    }

    @Override
    protected boolean supportsSingleImageInputAsBase64EncodedString() {
        return false;
    }

    @Override
    protected boolean supportsSingleImageInputAsPublicURL() {
        return false;
    }

    @Override
    protected boolean assertResponseId() {
        return false;
    }

    @Override
    protected boolean assertResponseModel() {
        return false;
    }

    @Override
    protected boolean assertTimesOnPartialResponseWasCalled() {
        return false; // when the request contains tools, the response is delivered in one piece once it is complete
    }

    @Override
    protected boolean assertThreads() {
        return false; // the handler is called on the thread that called chat(...)
    }
}
