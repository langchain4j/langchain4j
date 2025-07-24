package dev.langchain4j.model.openai;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
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
import me.kpavlov.aimocks.openai.MockOpenai;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@Execution(ExecutionMode.CONCURRENT)
class OpenAiChatModelErrorsTest {

    private static final MockOpenai MOCK = new MockOpenai();

    public static final Duration TIMEOUT = Duration.ofSeconds(3);

    ChatModel model = OpenAiChatModel.builder()
            .baseUrl(MOCK.baseUrl())
            .modelName(GPT_4_O_MINI)
            .timeout(TIMEOUT)
            .maxRetries(0)
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

        ChatModel model = OpenAiChatModel.builder()
                .baseUrl(MOCK.baseUrl())
                .modelName(GPT_4_O_MINI)
                .timeout(timeout)
                .maxRetries(0)
                .logRequests(true)
                .logResponses(true)
                .build();

        final var question = "Simulate timeout";
        MOCK.completion(req -> req.userMessageContains(question)).respondsError(res -> {
            res.delayMillis(TIMEOUT.multipliedBy(2).toMillis());
            res.setHttpStatus(HttpStatusCode.Companion.getNoContent());
            res.setBody("");
        });

        // when-then
        assertThatThrownBy(() -> model.chat(question))
                .isExactlyInstanceOf(dev.langchain4j.exception.TimeoutException.class);
    }

    @Test
    void should_handle_refusal() {

        // given
        final var userMessage = "does not matter";
        MOCK.completion(req -> req.userMessageContains(userMessage)).respondsError(res -> {
            res.setHttpStatus(HttpStatusCode.Companion.fromValue(200));
            // copied from https://platform.openai.com/docs/guides/structured-outputs/refusals?api-mode=chat#refusals
            // language=json
            res.setBody("""
                    {
                      "id": "chatcmpl-9nYAG9LPNonX8DAyrkwYfemr3C8HC",
                      "object": "chat.completion",
                      "created": 1721596428,
                      "model": "gpt-4o-2024-08-06",
                      "choices": [
                        {
                          "index": 0,
                          "message": {
                            "role": "assistant",
                            "refusal": "I'm sorry, I cannot assist with that request."
                          },
                          "logprobs": null,
                          "finish_reason": "stop"
                        }
                      ],
                      "usage": {
                        "prompt_tokens": 81,
                        "completion_tokens": 11,
                        "total_tokens": 92,
                        "completion_tokens_details": {
                          "reasoning_tokens": 0,
                          "accepted_prediction_tokens": 0,
                          "rejected_prediction_tokens": 0
                        }
                      },
                      "system_fingerprint": "fp_3407719c7f"
                    }
                    """);
        });

        // when-then
        assertThatThrownBy(() -> model.chat(userMessage))
                .isExactlyInstanceOf(dev.langchain4j.exception.ContentFilteredException.class)
                .hasMessage("I'm sorry, I cannot assist with that request.");
    }

    @AfterEach
    void afterEach() {
        MOCK.verifyNoUnmatchedRequests();
    }
}
