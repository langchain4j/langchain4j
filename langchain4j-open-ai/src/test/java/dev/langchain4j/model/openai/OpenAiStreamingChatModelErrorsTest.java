package dev.langchain4j.model.openai;

import static dev.langchain4j.model.LambdaStreamingResponseHandler.onPartialResponse;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import io.ktor.http.HttpStatusCode;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.stream.Stream;
import me.kpavlov.aimocks.openai.MockOpenai;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class OpenAiStreamingChatModelErrorsTest {

    private static final MockOpenai MOCK = new MockOpenai();

    public static final Duration TIMEOUT = Duration.ofSeconds(3);

    StreamingChatModel model = OpenAiStreamingChatModel.builder()
            .baseUrl(MOCK.baseUrl())
            .modelName(GPT_4_O_MINI)
            .timeout(TIMEOUT)
            .logRequests(true)
            .logResponses(true)
            .build();

    public static Stream<Arguments> errors() {
        return Stream.of(
                Arguments.of(400, InvalidRequestException.class),
                Arguments.of(401, AuthenticationException.class),
                Arguments.of(403, AuthenticationException.class),
                Arguments.of(404, ModelNotFoundException.class),
                Arguments.of(413, InvalidRequestException.class),
                Arguments.of(429, RateLimitException.class),
                Arguments.of(500, InternalServerException.class),
                Arguments.of(503, InternalServerException.class));
    }

    @ParameterizedTest
    @MethodSource("errors")
    void should_handle_error_responses(int httpStatusCode, Class<LangChain4jException> exception) throws Exception {

        // given
        final var question = "Return error: " + httpStatusCode;
        MOCK.completion(req -> req.userMessageContains(question)).respondsError(res -> {
            res.setHttpStatus(HttpStatusCode.Companion.fromValue(httpStatusCode));
            res.setBody("");
        });

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();
        StreamingChatResponseHandler handler = new ErrorHandler(futureError);

        // when
        model.chat(question, handler);

        // then
        Throwable error = futureError.get(30, SECONDS);

        assertThat(error).isExactlyInstanceOf(exception).satisfies(ex -> assertThat(
                        ((HttpException) ex.getCause()).statusCode())
                .as("statusCode")
                .isEqualTo(httpStatusCode));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void should_handle_timeout(int millis) throws Exception {

        // given
        Duration timeout = Duration.ofMillis(millis);

        StreamingChatModel model = OpenAiStreamingChatModel.builder()
                .baseUrl(MOCK.baseUrl())
                .modelName(GPT_4_O_MINI)
                .timeout(timeout)
                .logRequests(true)
                .logResponses(true)
                .build();

        final var question = "Simulate timeout";
        MOCK.completion(req -> req.userMessageContains(question)).respondsError(res -> {
            res.delayMillis(TIMEOUT.multipliedBy(2).toMillis());
            res.setHttpStatus(HttpStatusCode.Companion.getNoContent());
            res.setBody("");
        });

        CompletableFuture<Throwable> futureError = new CompletableFuture<>();
        StreamingChatResponseHandler handler = new ErrorHandler(futureError);

        // when
        model.chat(question, handler);

        // then
        Throwable error = futureError.get(30, SECONDS);

        assertThat(error).isExactlyInstanceOf(dev.langchain4j.exception.TimeoutException.class);
    }

    @ParameterizedTest
    @MethodSource("errors")
    void should_map_error_responses_on_the_reactive_publisher_path(
            int httpStatusCode, Class<LangChain4jException> exception) throws Exception {

        // given
        final var question = "Return error: " + httpStatusCode;
        MOCK.completion(req -> req.userMessageContains(question)).respondsError(res -> {
            res.setHttpStatus(HttpStatusCode.Companion.fromValue(httpStatusCode));
            res.setBody("");
        });

        ChatRequest request =
                ChatRequest.builder().messages(UserMessage.from(question)).build();
        CompletableFuture<Throwable> futureError = new CompletableFuture<>();

        // when
        model.chat(request).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ChatModelStreamingEvent item) {}

            @Override
            public void onError(Throwable throwable) {
                futureError.complete(throwable);
            }

            @Override
            public void onComplete() {
                futureError.completeExceptionally(new RuntimeException("onComplete must not be called"));
            }
        });

        // then: the reactive Publisher path maps the provider error to the same LangChain4j exception type as the
        // handler-based path (rather than forwarding the raw HttpException), so both APIs are consistent.
        Throwable error = futureError.get(30, SECONDS);
        assertThat(error).isExactlyInstanceOf(exception).satisfies(ex -> assertThat(
                        ((HttpException) ex.getCause()).statusCode())
                .as("statusCode")
                .isEqualTo(httpStatusCode));
    }

    @Test
    void should_throw_an_invalid_request_from_chat_with_the_shortest_lambda_handler() {

        // given: the JDK HTTP client rejects the header value with a line break while it builds the request
        StreamingChatModel model = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(new JdkHttpClientBuilder())
                .baseUrl("http://localhost:1/v1")
                .apiKey("sk-test\n")
                .modelName("gpt-5.4-mini")
                .build();

        // when-then: the handler has no error handler of its own, so its onError throws, and chat(...) throws that
        assertThatThrownBy(() -> model.chat("hi", onPartialResponse(token -> {})))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publisher_hook_should_signal_an_unsupported_parameter_instead_of_throwing() {

        // given
        MockHttpClient httpClient = new MockHttpClient();
        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test-key")
                .modelName(GPT_4_O_MINI)
                .build();
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hi"))
                .parameters(OpenAiChatRequestParameters.builder()
                        .modelName(GPT_4_O_MINI.toString())
                        .topK(3)
                        .build())
                .build();

        // when
        List<Object> signals = signals(model.doChat(request));

        // then
        assertThat(signals).hasSize(2);
        assertThat(signals.get(0)).isEqualTo("onSubscribe");
        assertThat(signals.get(1)).isInstanceOf(UnsupportedFeatureException.class);
        assertThat(httpClient.requests()).isEmpty();
    }

    @Test
    void publisher_hook_should_signal_a_failing_custom_headers_supplier_instead_of_throwing() {

        // given
        MockHttpClient httpClient = new MockHttpClient();
        IllegalStateException failure = new IllegalStateException("no token");
        OpenAiStreamingChatModel model = OpenAiStreamingChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(httpClient))
                .apiKey("test-key")
                .modelName(GPT_4_O_MINI)
                .customHeaders(() -> {
                    throw failure;
                })
                .build();

        // when
        List<Object> signals = signals(model.doChat(hookRequest()));

        // then
        assertThat(signals).containsExactly("onSubscribe", failure);
        assertThat(httpClient.requests()).isEmpty();
    }

    private static ChatRequest hookRequest() {
        // the hook is called directly, so the parameters are those of this model, as chat(ChatRequest) passes them
        return ChatRequest.builder()
                .messages(UserMessage.from("Hi"))
                .parameters(OpenAiChatRequestParameters.builder()
                        .modelName(GPT_4_O_MINI.toString())
                        .build())
                .build();
    }

    private static List<Object> signals(Flow.Publisher<ChatModelStreamingEvent> publisher) {
        List<Object> signals = new CopyOnWriteArrayList<>();
        publisher.subscribe(new Flow.Subscriber<>() {

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                signals.add("onSubscribe");
            }

            @Override
            public void onNext(ChatModelStreamingEvent event) {
                signals.add(event);
            }

            @Override
            public void onError(Throwable error) {
                signals.add(error);
            }

            @Override
            public void onComplete() {
                signals.add("onComplete");
            }
        });
        return signals;
    }

    private record ErrorHandler(CompletableFuture<Throwable> futureError) implements StreamingChatResponseHandler {

        @Override
        public void onPartialResponse(String partialResponse) {
            futureError.completeExceptionally(new RuntimeException("onPartialResponse must not be called"));
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            futureError.completeExceptionally(new RuntimeException("onCompleteResponse must not be called"));
        }

        @Override
        public void onError(Throwable error) {
            futureError.complete(error);
        }
    }
}
