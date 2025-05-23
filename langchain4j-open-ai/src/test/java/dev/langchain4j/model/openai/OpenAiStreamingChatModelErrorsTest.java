package dev.langchain4j.model.openai;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import io.ktor.http.HttpStatusCode;
import me.kpavlov.aimocks.openai.MockOpenai;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

@Disabled // TODO fix
class OpenAiStreamingChatModelErrorsTest {

    private static final MockOpenai MOCK = new MockOpenai();

    public static final Duration TIMEOUT = Duration.ofMillis(1000);

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

        assertThat(error)
                .isExactlyInstanceOf(exception)
                .satisfies(ex -> assertThat(((HttpException) ex.getCause()).statusCode())
                        .as("statusCode")
                        .isEqualTo(httpStatusCode));
    }

    @Test
    void should_handle_timeout() throws Exception {

        // given
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

        assertThat(error)
                .isExactlyInstanceOf(TimeoutException.class)
                .hasRootCauseExactlyInstanceOf(HttpTimeoutException.class);
    }

    class ErrorHandler implements StreamingChatResponseHandler {

        private final CompletableFuture<Throwable> futureError;

        ErrorHandler(CompletableFuture<Throwable> futureError) {
            this.futureError = futureError;
        }

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
