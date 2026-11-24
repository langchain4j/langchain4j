package dev.langchain4j.model.jitllm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.common.AbstractChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "MODEL", matches = ".+")
class JitLLMChatModelIT extends AbstractChatModelIT {

    private JitLLMChatModel model;
    private final List<JitLLMChatModel> modelsCreatedByTests = new ArrayList<>();

    static JitLLMChatModel.JitLLMChatModelBuilder modelBuilder() {
        return JitLLMChatModel.builder()
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
        modelsCreatedByTests.forEach(JitLLMChatModel::close);
        modelsCreatedByTests.clear();
    }

    @AfterAll
    void closeModel() {
        if (model != null) {
            model.close();
        }
    }

    @Override
    protected List<ChatModel> models() {
        return List.of(SharedModelView.of(model));
    }

    @Override
    protected ChatModel createModelWith(ChatRequestParameters parameters) {
        JitLLMChatModel created = JitLLMChatModel.builder()
                .modelPath(TestModelPath.fromEnvironment())
                .think(false)
                .defaultRequestParameters(parameters)
                .build();
        modelsCreatedByTests.add(created);
        return created;
    }

    @Override
    protected ChatRequestParameters createIntegrationSpecificParameters(int maxOutputTokens) {
        return DefaultChatRequestParameters.builder()
                .maxOutputTokens(maxOutputTokens)
                .build();
    }

    @Test
    void should_fail_after_close() {

        // given
        JitLLMChatModel closedModel = modelBuilder().build();
        closedModel.close();

        // when-then
        closedModel.close();
        assertThatThrownBy(() -> closedModel.chat("Hi"))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("The model is closed");
    }

    @Test
    void should_answer_concurrent_requests_independently() throws Exception {

        // given
        String germany = "What is the capital of Germany? Answer with the city name only.";
        String france = "What is the capital of France? Answer with the city name only.";

        // when
        List<Future<String>> answers;
        try (ExecutorService executor = Executors.newFixedThreadPool(4)) {
            answers = executor.invokeAll(List.of(
                    () -> model.chat(germany),
                    () -> model.chat(france),
                    () -> model.chat(germany),
                    () -> model.chat(france)));
        }

        // then
        assertThat(answers.get(0).get()).containsIgnoringCase("Berlin");
        assertThat(answers.get(1).get()).containsIgnoringCase("Paris");
        assertThat(answers.get(2).get()).containsIgnoringCase("Berlin");
        assertThat(answers.get(3).get()).containsIgnoringCase("Paris");
    }

    @Test
    void should_return_thinking_when_enabled() {

        // given
        JitLLMChatModel thinkingModel =
                modelBuilder().think(true).returnThinking(true).build();
        modelsCreatedByTests.add(thinkingModel);

        // when
        AiMessage aiMessage = thinkingModel
                .chat(UserMessage.from("What is the capital of Germany?"))
                .aiMessage();

        // then
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin").doesNotContain("<think>", "</think>");
        assertThat(aiMessage.thinking()).isNotBlank().doesNotContain("<think>", "</think>");
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
}
