package dev.langchain4j.model.vertexai.anthropic;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class VertexAiAnthropicPerRequestCustomHeadersTest {

    private static final GoogleCredentials CREDENTIALS = GoogleCredentials.create(new AccessToken("test-token", null));

    private static final ChatRequest CHAT_REQUEST = ChatRequest.builder()
            .messages(UserMessage.from("Hello"))
            .customHeaders(Map.of("X-Correlation-Id", "123"))
            .build();

    @Test
    void chat_model_should_reject_per_request_custom_headers() {

        VertexAiAnthropicChatModel model = VertexAiAnthropicChatModel.builder()
                .project("test-project")
                .location("us-east5")
                .modelName("claude-test")
                .credentials(CREDENTIALS)
                .build();

        assertThatThrownBy(() -> model.chat(CHAT_REQUEST))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("httpParameters");
    }

    @Test
    void streaming_chat_model_should_report_per_request_custom_headers_through_on_error() throws Exception {

        VertexAiAnthropicStreamingChatModel model = VertexAiAnthropicStreamingChatModel.builder()
                .project("test-project")
                .location("us-east5")
                .modelName("claude-test")
                .credentials(CREDENTIALS)
                .build();

        CompletableFuture<Throwable> error = new CompletableFuture<>();

        model.chat(CHAT_REQUEST, new StreamingChatResponseHandler() {

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                error.complete(null);
            }

            @Override
            public void onError(Throwable throwable) {
                error.complete(throwable);
            }
        });

        assertThat(error.get(5, SECONDS))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("httpParameters");
    }
}
