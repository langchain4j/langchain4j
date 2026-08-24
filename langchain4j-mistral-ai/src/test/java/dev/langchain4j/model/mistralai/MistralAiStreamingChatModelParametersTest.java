package dev.langchain4j.model.mistralai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;

class MistralAiStreamingChatModelParametersTest {

    @Test
    void default_request_parameters_expose_mistral_specific_type() {
        MistralAiStreamingChatModel model = MistralAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(new MockHttpClient()))
                .apiKey("dummy")
                .modelName("mistral-small-latest")
                .safePrompt(true)
                .randomSeed(42)
                .promptCacheKey("cache-key")
                .reasoningEffort("low")
                .serviceTier("standard_only")
                .build();

        assertThat(model.defaultRequestParameters()).isInstanceOf(MistralAiChatRequestParameters.class);
        assertThat(model.defaultRequestParameters().safePrompt()).isTrue();
        assertThat(model.defaultRequestParameters().randomSeed()).isEqualTo(42);
        assertThat(model.defaultRequestParameters().promptCacheKey()).isEqualTo("cache-key");
        assertThat(model.defaultRequestParameters().reasoningEffort()).isEqualTo("low");
        assertThat(model.defaultRequestParameters().serviceTier()).isEqualTo("standard_only");
    }

    @Test
    void per_call_parameters_override_model_defaults_in_the_request_body() {
        MockHttpClient mockHttpClient = new MockHttpClient();
        MistralAiStreamingChatModel model = MistralAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("dummy")
                .modelName("mistral-small-latest")
                .safePrompt(false)
                .promptCacheKey("cache-key")
                .reasoningEffort("low")
                .serviceTier("standard_only")
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
            model.chat(request, new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {}

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {}

                @Override
                public void onError(Throwable error) {}
            });
        } catch (Exception ignored) {
        }

        assertThat(mockHttpClient.request().body().replaceAll("\\s", ""))
                .contains("\"safe_prompt\":true")
                .contains("\"random_seed\":123")
                .contains("\"prompt_cache_key\":\"cache-key-override\"")
                .contains("\"reasoning_effort\":\"high\"")
                .contains("\"service_tier\":\"standard_only\"");
    }
}
