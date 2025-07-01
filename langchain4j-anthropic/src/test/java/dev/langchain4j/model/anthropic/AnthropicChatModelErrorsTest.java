package dev.langchain4j.model.anthropic;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_5_HAIKU_20241022;
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
import java.util.Random;
import java.util.stream.Stream;

import me.kpavlov.aimocks.anthropic.MockAnthropic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Execution(ExecutionMode.CONCURRENT)
class AnthropicChatModelErrorsTest {

    private static final MockAnthropic MOCK = new MockAnthropic(0, true);

    public static final Duration TIMEOUT = Duration.ofSeconds(2);

    private static final ChatModel model = AnthropicChatModel.builder()
            .apiKey("dummy-key")
            .baseUrl(MOCK.baseUrl() + "/v1")
            .modelName(CLAUDE_3_5_HAIKU_20241022)
            .maxTokens(20)
            .timeout(TIMEOUT)
            .logRequests(true)
            .logResponses(true)
            .build();

    private double seed;

    @BeforeEach
    void setUp() {
        seed = new Random().nextDouble(0.0, 1.0);
    }

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

        final var question = "What is the number: " + seed;
        final var message = "Error with seed: " + seed;

        // language=json
        final var responseBody =
                """
                        {
                          "type": "error",
                          "error": {
                            "type": "does not matter",
                            "message": "%s"
                          }
                        }
                        """
                        .formatted(message);

        MOCK.messages(req -> req.userMessageContains(question)).respondsError(res -> {
            res.setBody(responseBody);
            res.setHttpStatus(HttpStatusCode.Companion.fromValue(httpStatusCode));
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
        Duration timeout = Duration.ofMillis(millis);

        ChatModel model = AnthropicChatModel.builder()
                .apiKey("dummy-key")
                .baseUrl(MOCK.baseUrl() + "/v1")
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .maxTokens(20)
                .timeout(timeout)
                .logRequests(true)
                .logResponses(true)
                .build();

        final var question = "Simulate timeout " + System.currentTimeMillis();
        MOCK.messages(req -> req.userMessageContains(question)).respondsError(res -> {
            // don't really care about the response, just simulate a timeout
            res.delayMillis(TIMEOUT.plusMillis(250).toMillis());
            res.setHttpStatus(HttpStatusCode.Companion.getNoContent());
            res.setBody("");
        });

        // when-then
        assertThatThrownBy(() -> model.chat(question))
                .isExactlyInstanceOf(dev.langchain4j.exception.TimeoutException.class);
    }
}
