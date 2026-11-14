package dev.langchain4j.model.vertexai.gemini;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.HttpParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class VertexAiGeminiPerRequestCustomHeadersTest {

    private static final GenerationConfig GENERATION_CONFIG = GenerationConfig.newBuilder()
            .setTemperature(0.5f)
            .build();

    private static final ChatRequest CHAT_REQUEST = ChatRequest.builder()
            .messages(UserMessage.from("Hello"))
            .customHeaders(Map.of("X-Correlation-Id", "123"))
            .build();

    @Test
    void chat_model_should_reject_per_request_headers() throws IOException {

        // given
        GenerativeModel generativeModel = generativeModel();
        VertexAiGeminiChatModel model = new VertexAiGeminiChatModel(generativeModel, GENERATION_CONFIG);

        // when-then
        assertThatThrownBy(() -> model.chat(CHAT_REQUEST))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("httpParameters")
                .hasMessageContaining("customHeaders(...)")
                .hasMessageContaining("langchain4j-google-genai");
        verify(generativeModel, never()).generateContent(anyList());
    }

    @Test
    void streaming_chat_model_should_report_per_request_headers_as_unsupported_to_handler() throws IOException {

        // given
        GenerativeModel generativeModel = generativeModel();
        VertexAiGeminiStreamingChatModel model =
                new VertexAiGeminiStreamingChatModel(generativeModel, GENERATION_CONFIG);
        CompletableFuture<ChatResponse> futureResponse = new CompletableFuture<>();

        // when
        assertThatCode(() -> model.chat(CHAT_REQUEST, handler(futureResponse))).doesNotThrowAnyException();

        // then
        assertThat(futureResponse)
                .failsWithin(5, SECONDS)
                .withThrowableThat()
                .havingCause()
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .withMessageContaining("httpParameters");
        verify(generativeModel, never()).generateContentStream(anyList());
    }

    private static GenerativeModel generativeModel() {
        GenerativeModel generativeModel = mock(GenerativeModel.class);
        when(generativeModel.getModelName()).thenReturn("gemini-test");
        when(generativeModel.withGenerationConfig(any())).thenReturn(generativeModel);
        when(generativeModel.withTools(anyList())).thenReturn(generativeModel);
        when(generativeModel.withToolConfig(any())).thenReturn(generativeModel);
        return generativeModel;
    }

    private static StreamingChatResponseHandler handler(CompletableFuture<ChatResponse> futureResponse) {
        return new StreamingChatResponseHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                futureResponse.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                futureResponse.completeExceptionally(error);
            }
        };
    }

    @Test
    void http_parameters_without_headers_should_be_accepted() {

        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .httpParameters(HttpParameters.builder().customHeaders(Map.of()).build())
                .build();

        assertThatCode(() -> VertexAiGeminiRequestParameters.validateParameters(
                        parameters, ChatRequestParameters.builder().build()))
                .doesNotThrowAnyException();
    }
}

