package dev.langchain4j.model.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OllamaStreamingContentTypeTest {

    @Test
    void should_send_json_content_type_for_streaming_chat_requests() {
        // given
        MockHttpClient mockHttpClient = new MockHttpClient();

        OllamaStreamingChatModel model = OllamaStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
                .build();

        // when
        model.chat("test", new StreamingChatResponseHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {}

            @Override
            public void onError(Throwable error) {}
        });

        // then
        assertThat(mockHttpClient.request().headers()).containsEntry("Content-Type", List.of("application/json"));
    }

    @Test
    void should_send_json_content_type_for_streaming_completion_requests() {
        // given
        MockHttpClient mockHttpClient = new MockHttpClient();

        OllamaStreamingLanguageModel model = OllamaStreamingLanguageModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
                .build();

        // when
        model.generate("test", new StreamingResponseHandler<>() {

            @Override
            public void onNext(String token) {}

            @Override
            public void onError(Throwable error) {}
        });

        // then
        assertThat(mockHttpClient.request().headers()).containsEntry("Content-Type", List.of("application/json"));
    }

    @Test
    void should_allow_custom_content_type_to_override_default_for_streaming_chat_requests() {
        // given
        MockHttpClient mockHttpClient = new MockHttpClient();

        OllamaStreamingChatModel model = OllamaStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
                .customHeaders(Map.of("Content-Type", "application/custom"))
                .build();

        // when
        model.chat("test", new StreamingChatResponseHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {}

            @Override
            public void onError(Throwable error) {}
        });

        // then
        assertThat(mockHttpClient.request().headers()).containsEntry("Content-Type", List.of("application/custom"));
    }

    @Test
    void should_allow_custom_content_type_to_override_default_for_streaming_completion_requests() {
        // given
        MockHttpClient mockHttpClient = new MockHttpClient();

        OllamaStreamingLanguageModel model = OllamaStreamingLanguageModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .baseUrl("http://localhost:11434")
                .modelName("llama3")
                .customHeaders(Map.of("Content-Type", "application/custom"))
                .build();

        // when
        model.generate("test", new StreamingResponseHandler<>() {

            @Override
            public void onNext(String token) {}

            @Override
            public void onError(Throwable error) {}
        });

        // then
        assertThat(mockHttpClient.request().headers()).containsEntry("Content-Type", List.of("application/custom"));
    }
}
