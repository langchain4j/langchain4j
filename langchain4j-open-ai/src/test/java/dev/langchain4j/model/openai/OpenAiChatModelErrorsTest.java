package dev.langchain4j.model.openai;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.chat.request.ChatRequest;
import io.ktor.http.HttpStatusCode;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.stream.Stream;
import me.kpavlov.aimocks.openai.MockOpenai;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OpenAiChatModelErrorsTest {

    private static final MockOpenai MOCK = new MockOpenai();

    public static final Duration TIMEOUT = Duration.ofMillis(200);

    OpenAiChatModel model = OpenAiChatModel.builder()
            .baseUrl(MOCK.baseUrl())
            .apiKey("sk-proj-dummy-key")
            .organizationId("org-no-matter-what")
            .modelName(GPT_4_O_MINI)
            .temperature(0.0)
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
    void should_handle_error_responses(int httpStatusCode, Class<LangChain4jException> exception) {
        // given
        final var question = "Return error: " + httpStatusCode;
        MOCK.completion(req -> req.userMessageContains(question)).respondsError(res -> {
            res.setHttpStatus(HttpStatusCode.Companion.fromValue(httpStatusCode));
            res.setBody("");
        });

        // when-then
        assertThatExceptionOfType(exception)
                // when
                .isThrownBy(() -> model.chat(
                        ChatRequest.builder().messages(userMessage(question)).build()))
                .satisfies(ex -> assertThat(((HttpException) ex.getCause()).statusCode())
                        .as("statusCode")
                        .isEqualTo(httpStatusCode));
    }

    @Test
    void should_handle_timeout() {
        // given
        final var question = "Simulate timeout";
        MOCK.completion(req -> req.userMessageContains(question)).respondsError(res -> {
            try {
                Thread.sleep(TIMEOUT.plusMillis(100).toMillis());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            res.setHttpStatus(HttpStatusCode.Companion.getNoContent());
            res.setBody("");
        });

        // when-then
        assertThatExceptionOfType(RuntimeException.class) // TODO should be dev.langchain4j.exception.TimeoutException
                // when
                .isThrownBy(() -> model.chat(
                        ChatRequest.builder().messages(userMessage(question)).build()))
                .satisfies(ex -> assertThat(ex).hasCauseInstanceOf(HttpTimeoutException.class));
    }
}
