package dev.langchain4j.model.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class OpenAiResponsesRefusalTest {

    private static final String REFUSAL = "I cannot help with that request.";
    private static final String TEXT_CONTENT = "{\"type\":\"output_text\",\"text\":\"Hello\"}";
    private static final ChatRequest REQUEST =
            ChatRequest.builder().messages(UserMessage.from("Hello")).build();

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_throw_content_filtered_exception_for_refusal(boolean includeText) {
        OpenAiResponsesChatModel model = modelFor(responseJson("completed", refusalContent(includeText)));

        assertThatThrownBy(() -> model.chat(REQUEST))
                .isExactlyInstanceOf(ContentFilteredException.class)
                .hasMessage(REFUSAL);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_complete_async_chat_exceptionally_for_refusal(boolean includeText) {
        OpenAiResponsesChatModel model = modelFor(responseJson("completed", refusalContent(includeText)));

        assertThatThrownBy(() -> model.chatAsync(REQUEST).get(5, SECONDS))
                .isExactlyInstanceOf(ExecutionException.class)
                .hasCauseExactlyInstanceOf(ContentFilteredException.class)
                .hasRootCauseMessage(REFUSAL);
    }

    @ParameterizedTest
    @CsvSource({"completed,false", "completed,true", "incomplete,false", "incomplete,true"})
    void should_report_streamed_refusal_as_error_instead_of_completion(String status, boolean includeText) {
        StreamingChatResponseHandler handler = mock(StreamingChatResponseHandler.class);

        streamWith(status, refusalContent(includeText), handler);

        ArgumentCaptor<Throwable> error = ArgumentCaptor.forClass(Throwable.class);
        verify(handler).onError(error.capture());
        assertThat(error.getValue())
                .isExactlyInstanceOf(ContentFilteredException.class)
                .hasMessage(REFUSAL);
        verify(handler, never()).onCompleteResponse(any(ChatResponse.class));
    }

    @Test
    void should_fail_publisher_with_refusal_instead_of_completing() throws Exception {
        WireMockServer server =
                new WireMockServer(WireMockConfiguration.options().dynamicPort());
        try {
            server.start();
            String event = "{\"type\":\"response.completed\",\"response\":"
                    + responseJson("completed", refusalContent(false)).replace("\n", "") + "}";
            server.stubFor(post("/v1/responses")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "text/event-stream")
                            .withBody("data: " + event + "\n\n")));
            OpenAiResponsesStreamingChatModel model = OpenAiResponsesStreamingChatModel.builder()
                    .baseUrl(server.baseUrl() + "/v1")
                    .apiKey("dummy")
                    .modelName("gpt-5.4-mini")
                    .build();
            CompletableFuture<Throwable> failure = new CompletableFuture<>();
            AtomicBoolean completed = new AtomicBoolean();
            AtomicBoolean receivedCompleteResponse = new AtomicBoolean();

            model.chat(REQUEST).subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ChatModelStreamingEvent event) {
                    if (event instanceof CompleteResponse) {
                        receivedCompleteResponse.set(true);
                    }
                }

                @Override
                public void onError(Throwable error) {
                    failure.complete(error);
                }

                @Override
                public void onComplete() {
                    completed.set(true);
                    failure.completeExceptionally(
                            new AssertionError("Publisher completed instead of reporting refusal"));
                }
            });

            assertThat(failure.get(5, SECONDS))
                    .isExactlyInstanceOf(ContentFilteredException.class)
                    .hasMessage(REFUSAL);
            assertThat(receivedCompleteResponse).isFalse();
            assertThat(completed).isFalse();
        } finally {
            server.stop();
        }
    }

    @ParameterizedTest
    @MethodSource("contentWithoutRefusal")
    void should_preserve_text_when_refusal_is_absent_or_blank(String content) throws Exception {
        OpenAiResponsesChatModel model = modelFor(responseJson("completed", content));

        assertThat(model.chat(REQUEST).aiMessage().text()).isEqualTo("Hello");
        assertThat(model.chatAsync(REQUEST).get(5, SECONDS).aiMessage().text()).isEqualTo("Hello");

        StreamingChatResponseHandler handler = mock(StreamingChatResponseHandler.class);
        streamWith("completed", content, handler);

        ArgumentCaptor<ChatResponse> response = ArgumentCaptor.forClass(ChatResponse.class);
        verify(handler).onCompleteResponse(response.capture());
        assertThat(response.getValue().aiMessage().text()).isEqualTo("Hello");
        verify(handler, never()).onError(any(Throwable.class));
    }

    private static Stream<String> contentWithoutRefusal() {
        return Stream.of(
                TEXT_CONTENT,
                TEXT_CONTENT + ",{\"type\":\"refusal\",\"refusal\":\"\"}",
                TEXT_CONTENT + ",{\"type\":\"refusal\",\"refusal\":\"   \"}");
    }

    private static String refusalContent(boolean includeText) {
        return (includeText ? TEXT_CONTENT + "," : "") + "{\"type\":\"refusal\",\"refusal\":\"" + REFUSAL + "\"}";
    }

    private static String responseJson(String status, String content) {
        return """
                {
                  "id": "resp_1",
                  "model": "gpt-5.4-mini",
                  "object": "response",
                  "status": "%s",
                  "output": [{
                    "id": "msg_1",
                    "type": "message",
                    "role": "assistant",
                    "content": [%s]
                  }]
                }
                """.formatted(status, content);
    }

    private static OpenAiResponsesChatModel modelFor(String responseJson) {
        MockHttpClient httpClient = new MockHttpClient(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(responseJson)
                .build());
        return OpenAiResponsesChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .baseUrl("http://localhost")
                .apiKey("dummy")
                .modelName("gpt-5.4-mini")
                .build();
    }

    private static void streamWith(String status, String content, StreamingChatResponseHandler handler) {
        ServerSentEvent event = new ServerSentEvent(
                null, "{\"type\":\"response." + status + "\",\"response\":" + responseJson(status, content) + "}");
        OpenAiResponsesStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(MockHttpClient.thatAlwaysResponds(List.of(event))))
                .apiKey("dummy")
                .modelName("gpt-5.4-mini")
                .build()
                .chat(REQUEST, handler);
    }
}
