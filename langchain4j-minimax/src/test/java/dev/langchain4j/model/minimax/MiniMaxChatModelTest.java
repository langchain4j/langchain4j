package dev.langchain4j.model.minimax;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.ModelProvider;
import org.junit.jupiter.api.Test;

class MiniMaxChatModelTest {

    @Test
    void should_create_with_defaults() {
        MiniMaxChatModel model = MiniMaxChatModel.builder()
                .apiKey("test-api-key")
                .build();

        assertThat(model).isNotNull();
        assertThat(model.provider()).isEqualTo(ModelProvider.OTHER);
        assertThat(model.defaultRequestParameters()).isNotNull();
        assertThat(model.defaultRequestParameters().modelName()).isEqualTo("MiniMax-M2.7");
    }

    @Test
    void should_create_with_custom_model() {
        MiniMaxChatModel model = MiniMaxChatModel.builder()
                .apiKey("test-api-key")
                .modelName(MiniMaxChatModelName.MINIMAX_M2_7_HIGHSPEED)
                .build();

        assertThat(model).isNotNull();
        assertThat(model.defaultRequestParameters().modelName()).isEqualTo("MiniMax-M2.7-highspeed");
    }

    @Test
    void should_create_with_string_model_name() {
        MiniMaxChatModel model = MiniMaxChatModel.builder()
                .apiKey("test-api-key")
                .modelName("MiniMax-M2.7")
                .build();

        assertThat(model).isNotNull();
        assertThat(model.defaultRequestParameters().modelName()).isEqualTo("MiniMax-M2.7");
    }

    @Test
    void should_create_with_custom_base_url() {
        MiniMaxChatModel model = MiniMaxChatModel.builder()
                .apiKey("test-api-key")
                .baseUrl("https://custom-url.example.com/v1")
                .build();

        assertThat(model).isNotNull();
    }

    @Test
    void should_clamp_temperature() {
        MiniMaxChatModel model = MiniMaxChatModel.builder()
                .apiKey("test-api-key")
                .temperature(2.0)
                .build();

        assertThat(model).isNotNull();
        assertThat(model.defaultRequestParameters().temperature()).isEqualTo(1.0);
    }

    @Test
    void should_pass_valid_temperature() {
        MiniMaxChatModel model = MiniMaxChatModel.builder()
                .apiKey("test-api-key")
                .temperature(0.5)
                .build();

        assertThat(model).isNotNull();
        assertThat(model.defaultRequestParameters().temperature()).isEqualTo(0.5);
    }

    @Test
    void should_accept_zero_temperature() {
        MiniMaxChatModel model = MiniMaxChatModel.builder()
                .apiKey("test-api-key")
                .temperature(0.0)
                .build();

        assertThat(model).isNotNull();
        assertThat(model.defaultRequestParameters().temperature()).isEqualTo(0.0);
    }

    @Test
    void should_create_with_max_tokens() {
        MiniMaxChatModel model = MiniMaxChatModel.builder()
                .apiKey("test-api-key")
                .maxTokens(4096)
                .build();

        assertThat(model).isNotNull();
        assertThat(model.defaultRequestParameters().maxOutputTokens()).isEqualTo(4096);
    }

    @Test
    void builder_should_support_fluent_api() {
        MiniMaxChatModel.MiniMaxChatModelBuilder builder = MiniMaxChatModel.builder()
                .apiKey("test-api-key")
                .modelName("MiniMax-M2.7")
                .temperature(0.7)
                .topP(0.9)
                .maxTokens(2048)
                .maxRetries(3)
                .logRequests(true)
                .logResponses(true);

        MiniMaxChatModel model = builder.build();
        assertThat(model).isNotNull();
    }
}
