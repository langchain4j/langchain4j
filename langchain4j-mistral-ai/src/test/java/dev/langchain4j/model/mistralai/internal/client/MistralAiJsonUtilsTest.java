package dev.langchain4j.model.mistralai.internal.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MistralAiJsonUtilsTest {

    @Test
    void should_extract_provider_error_message() {
        String json = """
                {"error":{"message":"Service is too busy"}}""";
        assertThat(MistralAiJsonUtils.providerErrorMessageFrom(json)).isEqualTo("Service is too busy");
    }

    @Test
    void should_extract_provider_error_message_with_type_and_code() {
        // vLLM/LiteLLM send a bare error object carrying message, type and code
        String json = """
                {"error":{"message":"Internal server error","type":"InternalServerError","code":500}}""";
        assertThat(MistralAiJsonUtils.providerErrorMessageFrom(json))
                .isEqualTo("Internal server error (type: InternalServerError, code: 500)");
    }

    @Test
    void should_extract_provider_error_from_chunk_that_also_carries_choices() {
        // OpenRouter reports mid-stream errors as a regular chunk with a top-level "error"
        // object alongside "choices"
        String json = """
                {"id":"abc123","model":"mistral-small-latest","error":{"code":"server_error","message":"Provider disconnected unexpectedly"},"choices":[{"index":0,"delta":{"content":""},"finish_reason":"error"}]}""";
        assertThat(MistralAiJsonUtils.providerErrorMessageFrom(json))
                .isEqualTo("Provider disconnected unexpectedly (code: server_error)");
    }

    @Test
    void should_extract_provider_error_reported_as_plain_string() {
        String json = """
                {"error":"Service is too busy"}""";
        assertThat(MistralAiJsonUtils.providerErrorMessageFrom(json)).isEqualTo("Service is too busy");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // the word "error" merely appears inside streamed content
                "{\"choices\":[{\"index\":0,\"delta\":{\"content\":[{\"type\":\"text\",\"text\":\"no \\\"error\\\" "
                        + "here\"}]}}]}",
                // an "error" key nested below the top level is not an in-band provider error
                "{\"choices\":[],\"usage\":{\"error\":{\"message\":\"nested\"}}}"
            })
    void should_not_treat_non_top_level_error_as_provider_error(String json) {
        assertThat(MistralAiJsonUtils.providerErrorMessageFrom(json)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "not json at all", "[DONE]", "{\"choices\":[]}"})
    void should_return_null_when_no_provider_error_is_present(String json) {
        assertThat(MistralAiJsonUtils.providerErrorMessageFrom(json)).isNull();
    }
}
