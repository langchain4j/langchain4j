package dev.langchain4j.model.mistralai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

class MistralAiChatModelEmptyChoicesTest {

    /**
     * Mistral AI (and OpenAI-compatible servers fronted by it) may return a completion
     * response whose {@code choices} list is empty when the request is rejected upstream
     * (content filtering, quota, rate limiting, malformed response). Without a guard,
     * {@code MistralAiChatModel.doChat(...)} accesses {@code getChoices().get(0)} and throws
     * a cryptic {@code IndexOutOfBoundsException}. This test pins that an empty {@code choices}
     * list now surfaces as a clear {@code IllegalArgumentException} instead.
     */
    @Test
    void should_throw_IllegalArgumentException_when_response_has_empty_choices() {
        // given - upstream rejection: empty choices list
        String jsonResponse = """
                {
                    "id": "chatcmpl-empty-choices",
                    "object": "chat.completion",
                    "created": 1778075620,
                    "model": "mistralai/Ministral-3-8B-Instruct-2512",
                    "choices": [],
                    "usage": {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0}
                }
                """;

        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(jsonResponse)
                .build());

        ChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName("mistralai/Ministral-3-8B-Instruct-2512")
                .build();

        // when / then
        assertThatThrownBy(() -> model.chat(UserMessage.from("Bonjour")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("choices");
    }

    /**
     * The same {@code choices} access path also NPEs when the field is absent (or explicitly
     * {@code null}) in the response payload. The guard must cover the null case too.
     */
    @Test
    void should_throw_IllegalArgumentException_when_response_has_null_choices() {
        // given - no choices field at all (deserializes to null)
        String jsonResponse = """
                {
                    "id": "chatcmpl-null-choices",
                    "object": "chat.completion",
                    "created": 1778075620,
                    "model": "mistralai/Ministral-3-8B-Instruct-2512",
                    "usage": {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0}
                }
                """;

        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(jsonResponse)
                .build());

        ChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName("mistralai/Ministral-3-8B-Instruct-2512")
                .build();

        // when / then
        assertThatThrownBy(() -> model.chat(UserMessage.from("Bonjour")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("choices");
    }

    /**
     * Regression guard: a well-formed response with a single choice must still be parsed
     * correctly — the empty/null guard must not disturb the regular path.
     */
    @Test
    void should_return_chat_response_when_response_has_choices() {
        // given
        String jsonResponse = """
                {
                    "id": "chatcmpl-ok",
                    "object": "chat.completion",
                    "created": 1778075620,
                    "model": "mistralai/Ministral-3-8B-Instruct-2512",
                    "choices": [{
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": [{"type": "text", "text": "Bonjour, comment puis-je vous aider ?"}],
                            "tool_calls": null
                        },
                        "finish_reason": "stop"
                    }],
                    "usage": {"prompt_tokens": 5, "completion_tokens": 10, "total_tokens": 15}
                }
                """;

        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(jsonResponse)
                .build());

        ChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName("mistralai/Ministral-3-8B-Instruct-2512")
                .build();

        // when
        ChatResponse response = model.chat(UserMessage.from("Bonjour"));

        // then
        assertThat(response.aiMessage().text()).isEqualTo("Bonjour, comment puis-je vous aider ?");
        assertThat(response.aiMessage().hasToolExecutionRequests()).isFalse();
    }
}
