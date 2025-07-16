package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.chat.ChatModel;
import io.ktor.http.HttpStatusCode;
import java.time.Duration;
import java.util.stream.Stream;
import me.kpavlov.aimocks.gemini.MockGemini;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Execution(ExecutionMode.CONCURRENT)
class GoogleAiGeminiChatModelErrorsTest {

    private static final MockGemini MOCK = new MockGemini();

    public static final Duration TIMEOUT = Duration.ofSeconds(3);
    public static final String MODEL_NAME = "gemini-2.0-flash";

    final ChatModel model = GoogleAiGeminiChatModel.builder()
            .apiKey("dummy-api-key")
            .modelName(MODEL_NAME)
            .baseUrl(MOCK.baseUrl())
            .timeout(TIMEOUT)
            .maxRetries(0)
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
    void should_handle_error_responses(int httpStatusCode, Class<LangChain4jException> exception) {

        // given
        final var question = "Return error: " + httpStatusCode;
        MOCK.generateContent(req -> {
                    req.userMessageContains(question);
                    req.path("/models/%s:generateContent".formatted(MODEL_NAME));
                })
                .respondsError(res -> {
                    res.setHttpStatus(HttpStatusCode.Companion.fromValue(httpStatusCode));
                    res.setBody("");
                });

        // when-then
        assertThatThrownBy(() -> model.chat(question))
                .isExactlyInstanceOf(exception)
                .satisfies(ex -> assertThat(((HttpException) ex.getCause()).statusCode())
                        .as("statusCode")
                        .isEqualTo(httpStatusCode));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100})
    void should_handle_timeout(int millis) {

        // given
        final Duration timeout = Duration.ofMillis(millis);

        final ChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey("dummy-api-key2")
                .modelName(MODEL_NAME)
                .baseUrl(MOCK.baseUrl())
                .timeout(timeout)
                .maxRetries(0)
                .build();

        final var question = "Simulate timeout";
        MOCK.generateContent(req -> {
                    req.userMessageContains(question);
                    req.path("/models/%s:generateContent".formatted(MODEL_NAME));
                })
                .respondsError(res -> {
                    res.delayMillis(TIMEOUT.multipliedBy(2).toMillis());
                    res.setHttpStatus(HttpStatusCode.Companion.getNoContent());
                    res.setBody("");
                });

        // when-then
        assertThatThrownBy(() -> model.chat(question))
                .isExactlyInstanceOf(dev.langchain4j.exception.TimeoutException.class);
    }

    @AfterEach
    void afterEach() {
        MOCK.verifyNoUnmatchedRequests();
    }
}
