package dev.langchain4j.model.vertexai.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import org.junit.jupiter.api.Test;

class VertexAiGeminiRequestParametersTest {

    @Test
    void chat_model_should_return_configured_parameters_as_default_request_parameters() {
        VertexAiGeminiChatModel model = VertexAiGeminiChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("gemini-test")
                .temperature(0.5f)
                .topP(0.25f)
                .topK(7)
                .maxOutputTokens(100)
                .build();

        assertConfiguredParameters(model.defaultRequestParameters());
    }

    @Test
    void streaming_chat_model_should_return_configured_parameters_as_default_request_parameters() {
        VertexAiGeminiStreamingChatModel model = VertexAiGeminiStreamingChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("gemini-test")
                .temperature(0.5f)
                .topP(0.25f)
                .topK(7)
                .maxOutputTokens(100)
                .build();

        assertConfiguredParameters(model.defaultRequestParameters());
    }

    @Test
    void should_not_set_parameters_that_are_not_configured() {
        VertexAiGeminiChatModel model = VertexAiGeminiChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("gemini-test")
                .build();

        ChatRequestParameters parameters = model.defaultRequestParameters();

        assertThat(parameters.modelName()).isEqualTo("gemini-test");
        assertThat(parameters.temperature()).isNull();
        assertThat(parameters.topP()).isNull();
        assertThat(parameters.topK()).isNull();
        assertThat(parameters.maxOutputTokens()).isNull();
    }

    @Test
    void should_accept_request_values_equal_to_the_configured_ones() {
        ChatRequestParameters defaults = configured();

        assertThatNoException()
                .isThrownBy(() -> VertexAiGeminiRequestParameters.validateParameters(defaults, defaults));
    }

    @Test
    void should_accept_request_values_repeating_the_configured_float_values() {
        VertexAiGeminiChatModel model = VertexAiGeminiChatModel.builder()
                .project("does-not-matter")
                .location("does-not-matter")
                .modelName("gemini-test")
                .temperature(0.7f)
                .topP(0.9f)
                .build();
        ChatRequestParameters repeated =
                model.defaultRequestParameters().overrideWith(ChatRequestParameters.builder()
                        .temperature(0.7)
                        .topP(0.9)
                        .build());

        assertThatNoException()
                .isThrownBy(() -> VertexAiGeminiRequestParameters.validateParameters(
                        repeated, model.defaultRequestParameters()));
    }

    @Test
    void should_reject_request_values_that_differ_from_the_configured_ones() {
        ChatRequestParameters differentTemperature =
                configured().overrideWith(ChatRequestParameters.builder().temperature(0.9).build());

        assertThatThrownBy(() -> VertexAiGeminiRequestParameters.validateParameters(differentTemperature, configured()))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("'temperature' parameter is not supported yet by this model provider");
    }

    @Test
    void should_reject_request_values_when_nothing_is_configured() {
        ChatRequestParameters withModelName =
                ChatRequestParameters.builder().modelName("other-model").build();
        ChatRequestParameters nothingConfigured =
                ChatRequestParameters.builder().build();

        assertThatThrownBy(() -> VertexAiGeminiRequestParameters.validateParameters(withModelName, nothingConfigured))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("'modelName' parameter is not supported yet by this model provider");
    }

    @Test
    void should_still_reject_parameters_that_cannot_be_configured() {
        ChatRequestParameters withFrequencyPenalty =
                configured().overrideWith(ChatRequestParameters.builder().frequencyPenalty(0.1).build());

        assertThatThrownBy(() -> VertexAiGeminiRequestParameters.validateParameters(withFrequencyPenalty, configured()))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("'frequencyPenalty' parameter is not supported yet by this model provider");
    }

    private static ChatRequestParameters configured() {
        return ChatRequestParameters.builder()
                .modelName("gemini-test")
                .temperature(0.5)
                .topP(0.25)
                .topK(7)
                .maxOutputTokens(100)
                .build();
    }

    private static void assertConfiguredParameters(ChatRequestParameters parameters) {
        assertThat(parameters.modelName()).isEqualTo("gemini-test");
        assertThat(parameters.temperature()).isEqualTo(0.5);
        assertThat(parameters.topP()).isEqualTo(0.25);
        assertThat(parameters.topK()).isEqualTo(7);
        assertThat(parameters.maxOutputTokens()).isEqualTo(100);
    }
}
