package dev.langchain4j.model.minimax;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.ModelProvider;
import org.junit.jupiter.api.Test;

class MiniMaxStreamingChatModelTest {

    @Test
    void should_create_with_defaults() {
        MiniMaxStreamingChatModel model = MiniMaxStreamingChatModel.builder()
                .apiKey("test-api-key")
                .build();

        assertThat(model).isNotNull();
        assertThat(model.provider()).isEqualTo(ModelProvider.OTHER);
        assertThat(model.defaultRequestParameters()).isNotNull();
        assertThat(model.defaultRequestParameters().modelName()).isEqualTo("MiniMax-M2.7");
    }

    @Test
    void should_create_with_custom_model() {
        MiniMaxStreamingChatModel model = MiniMaxStreamingChatModel.builder()
                .apiKey("test-api-key")
                .modelName(MiniMaxChatModelName.MINIMAX_M2_7_HIGHSPEED)
                .build();

        assertThat(model).isNotNull();
        assertThat(model.defaultRequestParameters().modelName()).isEqualTo("MiniMax-M2.7-highspeed");
    }

    @Test
    void should_clamp_temperature() {
        MiniMaxStreamingChatModel model = MiniMaxStreamingChatModel.builder()
                .apiKey("test-api-key")
                .temperature(2.0)
                .build();

        assertThat(model).isNotNull();
        assertThat(model.defaultRequestParameters().temperature()).isEqualTo(1.0);
    }

    @Test
    void should_accept_zero_temperature() {
        MiniMaxStreamingChatModel model = MiniMaxStreamingChatModel.builder()
                .apiKey("test-api-key")
                .temperature(0.0)
                .build();

        assertThat(model).isNotNull();
        assertThat(model.defaultRequestParameters().temperature()).isEqualTo(0.0);
    }

    @Test
    void builder_should_support_fluent_api() {
        MiniMaxStreamingChatModel.MiniMaxStreamingChatModelBuilder builder = MiniMaxStreamingChatModel.builder()
                .apiKey("test-api-key")
                .modelName("MiniMax-M2.7")
                .temperature(0.7)
                .topP(0.9)
                .maxTokens(2048)
                .logRequests(true)
                .logResponses(true);

        MiniMaxStreamingChatModel model = builder.build();
        assertThat(model).isNotNull();
    }
}
