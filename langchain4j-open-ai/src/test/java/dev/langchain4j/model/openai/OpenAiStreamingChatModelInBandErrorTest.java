package dev.langchain4j.model.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Some OpenAI-compatible providers (e.g. DeepSeek) report errors with HTTP 200 inside the stream,
 * instead of with an error status code.
 */
class OpenAiStreamingChatModelInBandErrorTest {

    private static final String CONTENT_CHUNK =
            "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"}}]}\n\n";
    private static final String ERROR_CHUNK = "data: {\"error\":{\"message\":\"Service is too busy\"}}\n\n";
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
    void should_fail_with_provider_error_sent_as_server_sent_event(Api api) {

        // given
        respondWith(ERROR_CHUNK);

        // when-then
        assertFailsWith(chat(api), "Chat completion failed: Service is too busy");
    }

    @ParameterizedTest
    @EnumSource(Api.class)
    void should_fail_with_provider_error_sent_after_content(Api api) {

        // given
        respondWith(CONTENT_CHUNK + ERROR_CHUNK);

        // when-then
        assertFailsWith(chat(api), "Chat completion failed: Service is too busy");
    }

    @ParameterizedTest
    @EnumSource(Api.class)
    void should_fail_when_provider_error_is_sent_as_plain_json(Api api) {

        // given
        respondWith("{\"error\":{\"message\":\"Service is too busy\"}}");

        // when-then
        assertFailsWith(chat(api), "Chat completion failed: no choices returned in response");
    }

    @ParameterizedTest
    @EnumSource(Api.class)
    void should_fail_when_stream_is_empty(Api api) {

        // given
        respondWith("");

        // when-then
        assertFailsWith(chat(api), "Chat completion failed: no choices returned in response");
    }

    @ParameterizedTest
    @EnumSource(Api.class)
    void should_fail_with_provider_error_chunk_that_also_carries_choices(Api api) {

        // given
        // OpenRouter reports mid-stream errors as a regular chunk with a top-level "error" object
        // alongside "choices", with finish_reason "error"
        // See https://openrouter.ai/docs/api-reference/streaming
        String errorChunk = "data: {\"id\":\"cmpl-abc123\",\"object\":\"chat.completion.chunk\",\"created\":1739136000,"
                + "\"model\":\"gpt-4o-mini\",\"error\":{\"code\":\"server_error\","
                + "\"message\":\"Provider disconnected unexpectedly\"},"
                + "\"choices\":[{\"index\":0,\"delta\":{\"content\":\"\"},\"finish_reason\":\"error\"}]}\n\n";
        respondWith(CONTENT_CHUNK + errorChunk);

        // when-then
        assertFailsWith(chat(api), "Chat completion failed: Provider disconnected unexpectedly (code: server_error)");
    }

    @ParameterizedTest
    @EnumSource(Api.class)
    void should_fail_with_bare_provider_error_reporting_type_and_code(Api api) {

        // given
        // vLLM/LiteLLM send a bare error object carrying message, type, param and code
        String errorChunk = "data: {\"error\":{\"message\":\"Internal server error\",\"type\":\"InternalServerError\","
                + "\"param\":null,\"code\":500}}\n\n";
        respondWith(CONTENT_CHUNK + errorChunk + DONE);

        // when-then
        assertFailsWith(
                chat(api), "Chat completion failed: Internal server error (type: InternalServerError, code: 500)");
    }

    @ParameterizedTest
    @EnumSource(Api.class)
    void should_stream_content_that_merely_mentions_error(Api api) throws Exception {

        // given
        // the streamed text merely contains the word "error", even as a quoted JSON-like token:
        // this must not be misread as an in-band provider error
        String chunk1 = "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"No \"}}]}\n\n";
        String chunk2 = "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"\\\"error\\\" occurred\"}}]}\n\n";
        String finalChunk = "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n";
        respondWith(chunk1 + chunk2 + finalChunk + DONE);

        // when
        ChatResponse chatResponse = chat(api).get(10, SECONDS);

        // then
        assertThat(chatResponse.aiMessage().text()).isEqualTo("No \"error\" occurred");
        assertThat(chatResponse.metadata().finishReason()).isEqualTo(FinishReason.STOP);
    }

    @ParameterizedTest
    @EnumSource(Api.class)
    void should_complete_when_last_chunk_has_no_choices(Api api) throws Exception {

        // given
        String usageChunk =
                "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}\n\n";
        respondWith(CONTENT_CHUNK + usageChunk + DONE);

        // when
        ChatResponse chatResponse = chat(api).get(10, SECONDS);

        // then
        assertThat(chatResponse.aiMessage().text()).isEqualTo("Hello");
        assertThat(chatResponse.tokenUsage().totalTokenCount()).isEqualTo(2);
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
                .isExactlyInstanceOf(InternalServerException.class)
                .withMessage(expectedMessage);
    }
}
