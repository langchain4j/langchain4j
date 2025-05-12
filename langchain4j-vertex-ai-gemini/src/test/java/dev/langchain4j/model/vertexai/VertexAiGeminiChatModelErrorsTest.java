package dev.langchain4j.model.vertexai;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static dev.langchain4j.model.vertexai.VertexAiFactory.createTestVertexAI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.cloud.vertexai.VertexAI;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Execution(ExecutionMode.SAME_THREAD)
class VertexAiGeminiChatModelErrorsTest {

    private static final WireMockServer MOCK =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());
    private static final Logger log = LoggerFactory.getLogger(VertexAiGeminiChatModelErrorsTest.class);

    static {
        MOCK.start();
        Runtime.getRuntime().addShutdownHook(new Thread(MOCK::stop));
    }

    private static final Duration CI_TIMEOUT = Duration.ofSeconds(7);
    private static final Duration LOCAL_TIMEOUT = Duration.ofMillis(250);
    private static final Duration TIMEOUT;

    static {
        if (System.getenv("CI") != null) {
            log.info("Running on CI with timeout {} seconds", CI_TIMEOUT.getSeconds());
            TIMEOUT = CI_TIMEOUT;
        } else {
            log.info("Running locally with timeout {} milliseconds", LOCAL_TIMEOUT.toMillis());
            TIMEOUT = LOCAL_TIMEOUT;
        }
    }

    private final String project = "proj12345";
    private final String location = "us-central1";
    private final String modelName = "gemini-2.0-flash";

    private final VertexAI vertexAI = createTestVertexAI(MOCK.baseUrl(), project, location, TIMEOUT);

    ChatModel model = VertexAiGeminiChatModel.builder()
            .vertexAI(vertexAI)
            .modelName(modelName)
            .maxRetries(0) // do not retry
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
        final var userMessage = "Simulate error " + httpStatusCode + " @" + System.currentTimeMillis();
        MOCK.addStubMapping(WireMock.post("/v1/projects/" + project + "/locations/" + location
                        + "/publishers/google/models/" + modelName + ":generateContent?$alt=json;enum-encoding%3Dint")
                .withRequestBody(matchingJsonPath("$..text", containing(userMessage)))
                .willReturn(WireMock.aResponse().withStatus(httpStatusCode))
                .build());

        final var chatRequest = ChatRequest.builder()
                .messages(
                        SystemMessage.systemMessage("you are a smart-ass assistant"),
                        UserMessage.userMessage(userMessage))
                .build();

        // when-then
        assertThatThrownBy(() -> model.chat(chatRequest))
                .isExactlyInstanceOf(exception)
                .satisfies(ex -> assertThat(((HttpException) ex.getCause()).statusCode())
                        .as("statusCode")
                        .isEqualTo(httpStatusCode));
    }

    @Test
    void should_handle_timeout() {
        // given
        final var userMessage = "Simulate timeout @" + System.currentTimeMillis();
        MOCK.addStubMapping(WireMock.post("/v1/projects/" + project + "/locations/" + location
                        + "/publishers/google/models/" + modelName + ":generateContent?$alt=json;enum-encoding%3Dint")
                .withRequestBody(matchingJsonPath("$..text", containing(userMessage)))
                .willReturn(WireMock.aResponse()
                        .withFixedDelay((int) TIMEOUT.plusMillis(100).toMillis())
                        .withStatus(204))
                .build());

        log.debug("⏳ Mocking timeout: {}.", TIMEOUT);
        final var chatRequest = ChatRequest.builder()
                .messages(
                        SystemMessage.systemMessage("you are a smart-ass assistant"),
                        UserMessage.userMessage(userMessage))
                .build();

        // when-then
        assertThatThrownBy(() -> model.chat(chatRequest)).isExactlyInstanceOf(TimeoutException.class);
    }
}
