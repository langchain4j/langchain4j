package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiResponsesChatRequestParametersTest {

    @Test
    void should_store_server_tools() {
        List<Map<String, Object>> serverTools = List.of(Map.of("type", "web_search"));

        OpenAiResponsesChatRequestParameters parameters = OpenAiResponsesChatRequestParameters.builder()
                .serverTools(serverTools)
                .build();

        assertThat(parameters.serverTools()).containsExactlyElementsOf(serverTools);
    }

    @Test
    void should_override_server_tools() {
        OpenAiResponsesChatRequestParameters defaults = OpenAiResponsesChatRequestParameters.builder()
                .modelName("gpt-5.4-mini")
                .serverTools(List.of(Map.of("type", "web_search")))
                .build();

        OpenAiResponsesChatRequestParameters override = OpenAiResponsesChatRequestParameters.builder()
                .serverTools(List.of(Map.of("type", "file_search", "vector_store_ids", List.of("vs_1"))))
                .build();

        OpenAiResponsesChatRequestParameters merged = defaults.overrideWith(override);

        assertThat(merged.serverTools())
                .containsExactly(Map.of("type", "file_search", "vector_store_ids", List.of("vs_1")));
    }

    @Test
    void should_include_server_tools_in_equals_and_hash_code() {
        OpenAiResponsesChatRequestParameters first = OpenAiResponsesChatRequestParameters.builder()
                .serverTools(List.of(Map.of("type", "web_search")))
                .build();

        OpenAiResponsesChatRequestParameters second = OpenAiResponsesChatRequestParameters.builder()
                .serverTools(List.of(Map.of("type", "web_search")))
                .build();

        OpenAiResponsesChatRequestParameters third = OpenAiResponsesChatRequestParameters.builder()
                .serverTools(List.of(Map.of("type", "file_search")))
                .build();

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
        assertThat(first).isNotEqualTo(third);
    }

    @Test
    void should_store_server_tools_in_chat_model_default_request_parameters() {
        List<Map<String, Object>> serverTools = List.of(Map.of("type", "web_search"));

        OpenAiResponsesChatModel model = OpenAiResponsesChatModel.builder()
                .modelName("gpt-5.4-mini")
                .apiKey("banana")
                .serverTools(serverTools)
                .build();

        OpenAiResponsesChatRequestParameters parameters =
                (OpenAiResponsesChatRequestParameters) model.defaultRequestParameters();

        assertThat(parameters.serverTools()).containsExactlyElementsOf(serverTools);
    }

    @Test
    void should_store_server_tools_in_streaming_model_default_request_parameters() {
        List<Map<String, Object>> serverTools = List.of(Map.of("type", "file_search"));

        OpenAiResponsesStreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                .modelName("gpt-5.4-mini")
                .apiKey("banana")
                .serverTools(serverTools)
                .build();

        OpenAiResponsesChatRequestParameters parameters =
                (OpenAiResponsesChatRequestParameters) model.defaultRequestParameters();

        assertThat(parameters.serverTools()).containsExactlyElementsOf(serverTools);
    }

    @Test
    void should_override_prompt_cache_options() {
        OpenAiResponsesChatRequestParameters defaults = OpenAiResponsesChatRequestParameters.builder()
                .modelName("gpt-5.6")
                .promptCacheOptions(OpenAiPromptCacheOptions.implicit())
                .build();

        OpenAiResponsesChatRequestParameters override = OpenAiResponsesChatRequestParameters.builder()
                .promptCacheOptions(OpenAiPromptCacheOptions.explicit())
                .build();

        assertThat(defaults.overrideWith(override).promptCacheOptions()).isEqualTo(OpenAiPromptCacheOptions.explicit());
        assertThat(defaults.overrideWith(
                                OpenAiResponsesChatRequestParameters.builder().build())
                        .promptCacheOptions())
                .isEqualTo(OpenAiPromptCacheOptions.implicit());
    }

    @Test
    void should_include_prompt_cache_options_in_equals_and_hash_code() {
        OpenAiResponsesChatRequestParameters withOptions = OpenAiResponsesChatRequestParameters.builder()
                .promptCacheOptions(OpenAiPromptCacheOptions.explicit())
                .build();
        OpenAiResponsesChatRequestParameters withoutOptions =
                OpenAiResponsesChatRequestParameters.builder().build();

        assertThat(withOptions)
                .isNotEqualTo(withoutOptions)
                .doesNotHaveSameHashCodeAs(withoutOptions)
                .isEqualTo(OpenAiResponsesChatRequestParameters.builder()
                        .promptCacheOptions(OpenAiPromptCacheOptions.explicit())
                        .build());
    }

    @Test
    void should_store_prompt_cache_options_in_model_default_request_parameters() {
        OpenAiPromptCacheOptions promptCacheOptions = OpenAiPromptCacheOptions.builder()
                .mode(OpenAiPromptCacheOptions.MODE_EXPLICIT)
                .ttl(OpenAiPromptCacheOptions.TTL_30M)
                .build();

        OpenAiResponsesChatModel chatModel = OpenAiResponsesChatModel.builder()
                .apiKey("test")
                .modelName("gpt-5.6")
                .promptCacheOptions(promptCacheOptions)
                .build();
        OpenAiResponsesStreamingChatModel streamingChatModel = OpenAiResponsesStreamingChatModel.builder()
                .apiKey("test")
                .modelName("gpt-5.6")
                .promptCacheOptions(promptCacheOptions)
                .build();

        assertThat(((OpenAiResponsesChatRequestParameters) chatModel.defaultRequestParameters()).promptCacheOptions())
                .isEqualTo(promptCacheOptions);
        assertThat(((OpenAiResponsesChatRequestParameters) streamingChatModel.defaultRequestParameters())
                        .promptCacheOptions())
                .isEqualTo(promptCacheOptions);
    }
}
