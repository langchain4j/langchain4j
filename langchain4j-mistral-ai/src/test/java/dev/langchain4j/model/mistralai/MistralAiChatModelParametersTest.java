package dev.langchain4j.model.mistralai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.Test;

class MistralAiChatModelParametersTest {

    @Test
    void default_request_parameters_expose_mistral_specific_type() {
        MistralAiChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(new MockHttpClient()))
                .apiKey("dummy")
                .modelName("mistral-small-latest")
                .safePrompt(true)
                .randomSeed(42)
                .promptCacheKey("cache-key")
                .reasoningEffort("low")
                .serviceTier("standard_only")
                .build();

        assertThat(model.defaultRequestParameters())
                .isInstanceOf(MistralAiChatRequestParameters.class)
                .satisfies(parameters -> {
                    assertThat(parameters.safePrompt()).isTrue();
                    assertThat(parameters.randomSeed()).isEqualTo(42);
                    assertThat(parameters.promptCacheKey()).isEqualTo("cache-key");
                    assertThat(parameters.reasoningEffort()).isEqualTo("low");
                    assertThat(parameters.serviceTier()).isEqualTo("standard_only");
                });
    }

    @Test
    void mistral_specific_default_request_parameters_are_honored() {
        MistralAiChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(new MockHttpClient()))
                .apiKey("dummy")
                .modelName("mistral-small-latest")
                .defaultRequestParameters(MistralAiChatRequestParameters.builder()
                        .safePrompt(true)
                        .randomSeed(7)
                        .promptCacheKey("cache-key")
                        .reasoningEffort("low")
                        .serviceTier("standard_only")
                        .build())
                .build();

        assertThat(model.defaultRequestParameters()).satisfies(parameters -> {
            assertThat(parameters.safePrompt()).isTrue();
            assertThat(parameters.randomSeed()).isEqualTo(7);
            assertThat(parameters.promptCacheKey()).isEqualTo("cache-key");
            assertThat(parameters.reasoningEffort()).isEqualTo("low");
            assertThat(parameters.serviceTier()).isEqualTo("standard_only");
        });
    }

    @Test
    void per_call_parameters_override_model_defaults_in_the_request_body() {
        MockHttpClient mockHttpClient = new MockHttpClient();
        MistralAiChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("dummy")
                .modelName("mistral-small-latest")
                .safePrompt(false)
                .maxRetries(0)
                .promptCacheKey("cache-key")
                .reasoningEffort("low")
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(MistralAiChatRequestParameters.builder()
                        .safePrompt(true)
                        .randomSeed(123)
                        .promptCacheKey("cache-key-override")
                        .reasoningEffort("high")
                        .serviceTier("standard_only")
                        .build())
                .build();

        try {
            model.chat(request);
        } catch (Exception ignored) {
        }

        assertThat(mockHttpClient.request().body().replaceAll("\\s", ""))
                .contains("\"safe_prompt\":true")
                .contains("\"random_seed\":123")
                .contains("\"prompt_cache_key\":\"cache-key-override\"")
                .contains("\"reasoning_effort\":\"high\"")
                .contains("\"service_tier\":\"standard_only\"");
    }

    @Test
    void model_default_parameters_are_used_when_not_overridden_per_call() {
        MockHttpClient mockHttpClient = new MockHttpClient();
        MistralAiChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("dummy")
                .modelName("mistral-small-latest")
                .safePrompt(true)
                .maxRetries(0)
                .promptCacheKey("cache-key")
                .reasoningEffort("low")
                .serviceTier("standard_only")
                .build();

        try {
            model.chat("hi");
        } catch (Exception ignored) {
        }

        assertThat(mockHttpClient.request().body().replaceAll("\\s", ""))
                .contains("\"safe_prompt\":true")
                .contains("\"prompt_cache_key\":\"cache-key\"")
                .contains("\"reasoning_effort\":\"low\"")
                .contains("\"service_tier\":\"standard_only\"");
    }
}
