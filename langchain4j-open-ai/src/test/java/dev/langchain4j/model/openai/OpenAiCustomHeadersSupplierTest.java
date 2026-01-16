package dev.langchain4j.model.openai;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

import io.ktor.http.HttpStatusCode;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import me.kpavlov.aimocks.openai.MockOpenai;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

@Execution(ExecutionMode.CONCURRENT)
class OpenAiCustomHeadersSupplierTest {

    private static final MockOpenai MOCK = new MockOpenai();
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final String MOCK_RESPONSE =
            """
            {
              "id": "chatcmpl-test",
              "object": "chat.completion",
              "created": 1721596428,
              "model": "gpt-4o-mini",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "Hello!"
                  },
                  "finish_reason": "stop"
                }
              ],
              "usage": {
                "prompt_tokens": 10,
                "completion_tokens": 5,
                "total_tokens": 15
              }
            }
            """;

    @Test
    void should_call_supplier_for_each_request_with_chat_model() {
        // given
        AtomicInteger callCount = new AtomicInteger(0);

        Supplier<Map<String, String>> headerSupplier = () -> {
            callCount.incrementAndGet();
            return Map.of("X-Custom-Token", "token-" + callCount.get());
        };

        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(MOCK.baseUrl())
                .modelName(GPT_4_O_MINI)
                .timeout(TIMEOUT)
                .maxRetries(0)
                .customHeaders(headerSupplier)
                .build();

        MOCK.completion(req -> req.userMessageContains("first")).respondsError(res -> {
            res.setHttpStatus(HttpStatusCode.Companion.fromValue(200));
            res.setBody(MOCK_RESPONSE);
        });
        MOCK.completion(req -> req.userMessageContains("second")).respondsError(res -> {
            res.setHttpStatus(HttpStatusCode.Companion.fromValue(200));
            res.setBody(MOCK_RESPONSE);
        });

        // when
        model.chat("first");
        int countAfterFirst = callCount.get();
        model.chat("second");
        int countAfterSecond = callCount.get();

        // then
        assertThat(countAfterFirst).isGreaterThan(0);
        assertThat(countAfterSecond).isGreaterThan(countAfterFirst);
    }

    @Test
    void should_work_with_static_map_for_backwards_compatibility() {
        // given
        Map<String, String> staticHeaders = Map.of("X-Static-Header", "static-value");

        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(MOCK.baseUrl())
                .modelName(GPT_4_O_MINI)
                .timeout(TIMEOUT)
                .maxRetries(0)
                .customHeaders(staticHeaders)
                .build();

        MOCK.completion(req -> req.userMessageContains("test")).respondsError(res -> {
            res.setHttpStatus(HttpStatusCode.Companion.fromValue(200));
            res.setBody(MOCK_RESPONSE);
        });

        // when-then
        model.chat("test");
    }

    @Test
    void should_handle_null_from_supplier() {
        // given
        Supplier<Map<String, String>> nullSupplier = () -> null;

        OpenAiChatModel model = OpenAiChatModel.builder()
                .baseUrl(MOCK.baseUrl())
                .modelName(GPT_4_O_MINI)
                .timeout(TIMEOUT)
                .maxRetries(0)
                .customHeaders(nullSupplier)
                .build();

        MOCK.completion(req -> req.userMessageContains("test")).respondsError(res -> {
            res.setHttpStatus(HttpStatusCode.Companion.fromValue(200));
            res.setBody(MOCK_RESPONSE);
        });

        // when-then
        model.chat("test");
    }

    @AfterEach
    void afterEach() {
        MOCK.verifyNoUnmatchedRequests();
    }
}
