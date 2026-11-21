package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OpenAiRawFinishReasonTest {

    static Stream<Arguments> reasons() {
        return Stream.of(
                Arguments.of("stop", FinishReason.STOP),
                Arguments.of("aborted", null),
                Arguments.of("insufficient_system_resource", null),
                Arguments.of(null, null));
    }

    @ParameterizedTest
    @MethodSource("reasons")
    void should_preserve_raw_finish_reason_in_chat_response(String rawReason, FinishReason mappedReason) {
        String body = """
                {"choices":[{"index":0,"message":{"role":"assistant","content":"Hello"}%s}]}
                """.formatted(finishReasonField(rawReason));
        MockHttpClient client = MockHttpClient.thatAlwaysResponds(
                SuccessfulHttpResponse.builder().statusCode(200).body(body).build());
        OpenAiChatModel model = OpenAiChatModel.builder()
                .modelName("test-model")
                .apiKey("test-key")
                .httpClientBuilder(new MockHttpClientBuilder(client))
                .build();

        ChatResponse response = model.chat(
                ChatRequest.builder().messages(UserMessage.from("Hi")).build());

        assertFinishReasons(response, rawReason, mappedReason);
    }

    @ParameterizedTest
    @MethodSource("reasons")
    void should_preserve_raw_finish_reason_in_final_streaming_response(String rawReason, FinishReason mappedReason)
            throws Exception {
        var content = new ServerSentEvent(null, "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"}}]}");
        var terminal = new ServerSentEvent(null, """
                {"choices":[{"index":0,"delta":{}%s}]}
                """.formatted(finishReasonField(rawReason)));
        var usage = new ServerSentEvent(null, """
                {"choices":[],"usage":{"prompt_tokens":2,"completion_tokens":1,"total_tokens":3}}
                """);
        MockHttpClient client = MockHttpClient.thatAlwaysResponds(
                List.of(content, terminal, usage, new ServerSentEvent(null, "[DONE]")));
        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .modelName("test-model")
                .apiKey("test-key")
                .httpClientBuilder(new MockHttpClientBuilder(client))
                .build();
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();

        model.chat("Hi", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                response.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                response.completeExceptionally(error);
            }
        });

        assertFinishReasons(response.get(5, TimeUnit.SECONDS), rawReason, mappedReason);
    }

    private static String finishReasonField(String reason) {
        return reason == null ? "" : ",\"finish_reason\":\"" + reason + "\"";
    }

    private static void assertFinishReasons(ChatResponse response, String rawReason, FinishReason mappedReason) {
        assertThat(response.aiMessage().text()).isEqualTo("Hello");
        assertThat(response.finishReason()).isEqualTo(mappedReason);
        var metadata = (OpenAiChatResponseMetadata) response.metadata();
        assertThat(metadata.rawFinishReason()).isEqualTo(rawReason);
        assertThat(metadata.toBuilder().build()).isEqualTo(metadata).hasSameHashCodeAs(metadata);
    }
}
