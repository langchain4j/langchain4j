package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.LazyEvaluationConfig;
import dev.langchain4j.agent.tool.LazyEvaluationMode;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for lazy evaluation configuration in AiServices builder.
 */
class AiServicesLazyEvaluationConfigTest {

    interface TestService {
        String chat(String userMessage);
    }

    private final ChatModel chatModel = ChatModelMock.thatAlwaysResponds("response");

    @Test
    void should_accept_lazy_evaluation_config() {
        LazyEvaluationConfig config =
                LazyEvaluationConfig.builder().mode(LazyEvaluationMode.ENABLED).build();

        TestService service = AiServices.builder(TestService.class)
                .chatModel(chatModel)
                .lazyEvaluationConfig(config)
                .build();

        assertThat(service).isNotNull();
    }

    @Test
    void should_throw_when_lazy_evaluation_config_is_null() {
        assertThatThrownBy(() -> AiServices.builder(TestService.class)
                        .chatModel(chatModel)
                        .lazyEvaluationConfig(null)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lazyEvaluationConfig");
    }

    @Test
    void should_enable_lazy_evaluation_with_convenience_method() {
        TestService service = AiServices.builder(TestService.class)
                .chatModel(chatModel)
                .enableLazyEvaluation()
                .build();

        assertThat(service).isNotNull();
    }

    @Test
    void should_allow_chaining_lazy_evaluation_config() {
        LazyEvaluationConfig config =
                LazyEvaluationConfig.builder().mode(LazyEvaluationMode.AUTO).build();

        TestService service = AiServices.builder(TestService.class)
                .chatModel(chatModel)
                .lazyEvaluationConfig(config)
                .enableLazyEvaluation() // Should override previous config
                .build();

        assertThat(service).isNotNull();
    }

    @Test
    void should_allow_chaining_enable_lazy_evaluation_first() {
        LazyEvaluationConfig config =
                LazyEvaluationConfig.builder().mode(LazyEvaluationMode.DISABLED).build();

        TestService service = AiServices.builder(TestService.class)
                .chatModel(chatModel)
                .enableLazyEvaluation()
                .lazyEvaluationConfig(config) // Should override previous config
                .build();

        assertThat(service).isNotNull();
    }

    @Test
    void should_work_with_different_lazy_evaluation_modes() {
        for (LazyEvaluationMode mode : LazyEvaluationMode.values()) {
            LazyEvaluationConfig config =
                    LazyEvaluationConfig.builder().mode(mode).build();

            TestService service = AiServices.builder(TestService.class)
                    .chatModel(chatModel)
                    .lazyEvaluationConfig(config)
                    .build();

            assertThat(service).isNotNull();
        }
    }

    @Test
    void should_work_with_complex_lazy_evaluation_config() {
        LazyEvaluationConfig config = LazyEvaluationConfig.builder()
                .mode(LazyEvaluationMode.ENABLED)
                .addLazyTool("tool1")
                .addEagerTool("tool2")
                .enablePerformanceMonitoring(true)
                .build();

        TestService service = AiServices.builder(TestService.class)
                .chatModel(chatModel)
                .lazyEvaluationConfig(config)
                .build();

        assertThat(service).isNotNull();
    }

    @Test
    void should_maintain_fluent_api_with_lazy_evaluation_config() {
        LazyEvaluationConfig config =
                LazyEvaluationConfig.builder().mode(LazyEvaluationMode.ENABLED).build();

        // Test that the builder returns the correct type for method chaining
        AiServices<TestService> builder =
                AiServices.builder(TestService.class).chatModel(chatModel).lazyEvaluationConfig(config);

        assertThat(builder).isNotNull();

        TestService service = builder.build();
        assertThat(service).isNotNull();
    }

    @Test
    void should_maintain_fluent_api_with_enable_lazy_evaluation() {
        // Test that the builder returns the correct type for method chaining
        AiServices<TestService> builder =
                AiServices.builder(TestService.class).chatModel(chatModel).enableLazyEvaluation();

        assertThat(builder).isNotNull();

        TestService service = builder.build();
        assertThat(service).isNotNull();
    }

    @Test
    void should_work_without_lazy_evaluation_config() {
        // Ensure that not setting lazy evaluation config doesn't break anything
        TestService service =
                AiServices.builder(TestService.class).chatModel(chatModel).build();

        assertThat(service).isNotNull();
    }

    @Test
    void should_work_with_other_builder_methods() {
        LazyEvaluationConfig config =
                LazyEvaluationConfig.builder().mode(LazyEvaluationMode.ENABLED).build();

        TestService service = AiServices.builder(TestService.class)
                .chatModel(chatModel)
                .systemMessageProvider(chatMemoryId -> "System message")
                .lazyEvaluationConfig(config)
                .build();

        assertThat(service).isNotNull();
    }
}
