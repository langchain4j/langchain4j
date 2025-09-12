package dev.langchain4j.model.chat.common;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import io.ktor.http.HttpStatusCode;
import java.time.Duration;
import java.util.Random;
import java.util.stream.Stream;
import me.kpavlov.aimocks.core.AbstractBuildingStep;
import me.kpavlov.aimocks.core.AbstractMockLlm;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.CONCURRENT)
public abstract class AbstractChatModelErrorsTest<MODEL extends ChatModel, MOCK extends AbstractMockLlm> {

    protected final MOCK mock;

    protected AbstractChatModelErrorsTest(MOCK mock) {
        this.mock = mock;
    }

    protected abstract MODEL createModel(double temperature, @Nullable Duration timeout);

    protected abstract AbstractBuildingStep<?, ?> whenMockMatched(String question, double temperature);

    protected String errorResponseBody(String message) {
        return message;
    }

    @AfterEach
    public void afterEach() {
        mock.verifyNoUnmatchedRequests();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("errors")
    public void should_handle_error_responses(
            int httpStatusCode, Class<? extends LangChain4jException> expectedException) {
        double temperature = 0.1 + new Random().nextDouble() * 0.9;
        String question = "Return error: " + httpStatusCode;
        String message = "Error : " + httpStatusCode;

        whenMockMatched(question, temperature).respondsError(response -> {
            response.setHttpStatus(HttpStatusCode.Companion.fromValue(httpStatusCode));
            response.setBody(errorResponseBody(message));
        });

        MODEL model = createModel(temperature, null);

        Assertions.assertThatThrownBy(() -> model.chat(ChatRequest.builder()
                        .temperature(temperature)
                        .messages(UserMessage.userMessage(question))
                        .build()))
                .isExactlyInstanceOf(expectedException)
                .satisfies(ex -> {
                    Assertions.assertThat(ex).isInstanceOf(expectedException);
                    Assertions.assertThat(ex.getCause()).isInstanceOf(HttpException.class);
                    Assertions.assertThat(((HttpException) ex.getCause()).statusCode())
                            .isEqualTo(httpStatusCode);
                });
    }

    @ParameterizedTest(name = "Timeout: {0}ms")
    @ValueSource(ints = {100, 500, 1000})
    public void should_handle_timeout(long millis) {
        double temperature = 0.1 + new Random().nextDouble() * 0.9;
        MODEL model = createModel(temperature, Duration.ofMillis(millis));
        String question = "Simulate timeout";

        whenMockMatched(question, temperature).respondsError(response -> {
            response.setHttpStatus(HttpStatusCode.Companion.getOK());
            response.delayMillis(millis * 2);
        });

        Assertions.assertThatThrownBy(() -> model.chat(ChatRequest.builder()
                        .temperature(temperature)
                        .messages(UserMessage.userMessage(question))
                        .build()))
                .isExactlyInstanceOf(TimeoutException.class);
    }

    protected Stream<Arguments> errors() {
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
}
