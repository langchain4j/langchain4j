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
                .build();

        assertThat(model.defaultRequestParameters()).isInstanceOf(MistralAiChatRequestParameters.class);
        assertThat(model.defaultRequestParameters().safePrompt()).isTrue();
        assertThat(model.defaultRequestParameters().randomSeed()).isEqualTo(42);
    }

    @Test
    void per_call_parameters_override_model_defaults_in_the_request_body() {
        MockHttpClient mockHttpClient = new MockHttpClient();
        MistralAiStreamingChatModel model = MistralAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("dummy")
                .modelName("mistral-small-latest")
                .safePrompt(false)
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(MistralAiChatRequestParameters.builder()
                        .safePrompt(true)
                        .randomSeed(123)
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
                .contains("\"random_seed\":123");
    }
}
