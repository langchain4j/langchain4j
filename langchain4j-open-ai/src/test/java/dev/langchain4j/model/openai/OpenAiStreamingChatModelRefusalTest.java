package dev.langchain4j.model.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * OpenAI may refuse to answer a request (e.g. due to content filtering) and stream the refusal text
 * inside the delta ({@code "refusal"} field) with HTTP 200, instead of completing normally.
 * The streaming API must surface that refusal, consistently with the non-streaming API,
 * which throws {@link ContentFilteredException}.
 * See <a href="https://github.com/langchain4j/langchain4j/issues/5813">issue #5813</a>.
 */
class OpenAiStreamingChatModelRefusalTest {

    private static final String REFUSAL_CHUNK =
            "data: {\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"refusal\":\"I cannot help with that request.\"},\"finish_reason\":\"stop\"}]}\n\n";
    private static final String REFUSAL_FIRST_CHUNK =
            "data: {\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"refusal\":\"I'm sorry, \"}}]}\n\n";
    private static final String REFUSAL_SECOND_CHUNK =
            "data: {\"choices\":[{\"index\":0,\"delta\":{\"refusal\":\"I cannot help with that request.\"},\"finish_reason\":\"stop\"}]}\n\n";
    private static final String CONTENT_CHUNK =
            "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"}}]}\n\n";
    private static final String DONE = "data: [DONE]\n\n";

    enum Api {
        HANDLER,
        PUBLISHER
    }

    private WireMockServer wireMockServer;
    private StreamingChatModel model;

    @BeforeEach
    void startServer() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        model = OpenAiStreamingChatModel.builder()
                .baseUrl("http://localhost:" + wireMockServer.port() + "/v1")
                .apiKey("test-key")
                .modelName("gpt-4o-mini")
                .build();
    }

    @AfterEach
    void stopServer() {
        wireMockServer.stop();
    }

    @ParameterizedTest
    @EnumSource(Api.class)
    void should_fail_with_content_filtered_exception_when_stream_contains_refusal(Api api) {

        // given
        respondWith(REFUSAL_CHUNK + DONE);

        // when-then
        assertFailsWith(chat(api), "I cannot help with that request.");
    }

    @ParameterizedTest
    @EnumSource(Api.class)
    void should_accumulate_refusal_streamed_across_multiple_chunks(Api api) {

        // given
        respondWith(REFUSAL_FIRST_CHUNK + REFUSAL_SECOND_CHUNK + DONE);

        // when-then
        assertFailsWith(chat(api), "I'm sorry, I cannot help with that request.");
    }

    @ParameterizedTest
    @EnumSource(Api.class)
    void should_fail_with_content_filtered_exception_when_refusal_follows_content(Api api) {

        // given
        respondWith(CONTENT_CHUNK + REFUSAL_CHUNK + DONE);

        // when-then
        assertFailsWith(chat(api), "I cannot help with that request.");
    }

    @ParameterizedTest
    @EnumSource(Api.class)
    void should_complete_normally_when_stream_contains_no_refusal(Api api) throws Exception {

        // given
        respondWith(CONTENT_CHUNK + DONE);

        // when
        ChatResponse chatResponse = chat(api).get(10, SECONDS);

        // then
        assertThat(chatResponse.aiMessage().text()).isEqualTo("Hello");
    }

    private void respondWith(String body) {
        wireMockServer.stubFor(post(urlEqualTo("/v1/chat/completions"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody(body)));
    }

    private CompletableFuture<ChatResponse> chat(Api api) {
        ChatRequest request =
                ChatRequest.builder().messages(UserMessage.from("hi")).build();
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        if (api == Api.HANDLER) {
            model.chat(request, new StreamingChatResponseHandler() {

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    future.complete(completeResponse);
                }

                @Override
                public void onError(Throwable error) {
                    future.completeExceptionally(error);
                }
            });
        } else {
            model.chat(request).subscribe(new Flow.Subscriber<>() {

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(ChatModelStreamingEvent event) {
                    if (event instanceof CompleteResponse completeResponse) {
                        future.complete(completeResponse.chatResponse());
                    }
                }

                @Override
                public void onError(Throwable error) {
                    future.completeExceptionally(error);
                }

                @Override
                public void onComplete() {
                    future.completeExceptionally(new AssertionError("completed without CompleteResponse"));
                }
            });
        }

        return future;
    }

    private static void assertFailsWith(CompletableFuture<ChatResponse> future, String expectedMessage) {
        assertThat(future)
                .failsWithin(10, SECONDS)
                .withThrowableOfType(ExecutionException.class)
                .havingCause()
                .isExactlyInstanceOf(ContentFilteredException.class)
                .withMessage(expectedMessage);
    }
}
