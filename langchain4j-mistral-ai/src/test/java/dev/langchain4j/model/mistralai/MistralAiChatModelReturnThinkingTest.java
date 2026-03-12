package dev.langchain4j.model.mistralai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

class MistralAiChatModelReturnThinkingTest {

    @Test
    void should_return_thinking_when_returnThinking_is_true() {
        // given
        String jsonResponse =
                """
                {
                    "id": "abc123",
                    "created": 1769421662,
                    "model": "magistral-medium-latest",
                    "choices": [{
                        "index": 0,
                        "finish_reason": "stop",
                        "message": {
                            "role": "assistant",
                            "content": [
                                {"type": "thinking", "thinking": [{"type": "text", "text": "Let me think about this problem..."}]},
                                {"type": "text", "text": "The answer is 42."}
                            ]
                        }
                    }],
                    "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30, "num_cached_tokens": 0}
                }
                """;

        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(jsonResponse)
                .build());

        ChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName(MistralAiChatModelName.MAGISTRAL_MEDIUM_LATEST)
                .returnThinking(true)
                .build();

        // when
        ChatResponse response = model.chat(UserMessage.from("What is the answer?"));

        // then
        assertThat(response.aiMessage().text()).isEqualTo("The answer is 42.");
        assertThat(response.aiMessage().thinking()).isEqualTo("Let me think about this problem...");
    }

    @Test
    void should_NOT_return_thinking_when_returnThinking_is_false() {
        // given
        String jsonResponse =
                """
                {
                    "id": "abc123",
                    "created": 1769421662,
                    "model": "magistral-medium-latest",
                    "choices": [{
                        "index": 0,
                        "finish_reason": "stop",
                        "message": {
                            "role": "assistant",
                            "content": [
                                {"type": "thinking", "thinking": [{"type": "text", "text": "Let me think about this problem..."}]},
                                {"type": "text", "text": "The answer is 42."}
                            ]
                        }
                    }],
                    "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30, "num_cached_tokens": 0}
                }
                """;

        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(jsonResponse)
                .build());

        ChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName(MistralAiChatModelName.MAGISTRAL_MEDIUM_LATEST)
                .returnThinking(false)
                .build();

        // when
        ChatResponse response = model.chat(UserMessage.from("What is the answer?"));

        // then
        assertThat(response.aiMessage().text()).isEqualTo("The answer is 42.");
        assertThat(response.aiMessage().thinking()).isNull();
    }

    @Test
    void should_handle_empty_thinking_array() {
        // given - real Magistral response: text, empty thinking, text
        String jsonResponse =
                """
                {
                    "id": "966f7ab3efd24195b686603d34aa41a3",
                    "created": 1769421662,
                    "model": "magistral-medium-latest",
                    "choices": [{
                        "index": 0,
                        "finish_reason": "stop",
                        "message": {
                            "role": "assistant",
                            "tool_calls": null,
                            "content": [
                                {"type": "text", "text": "Let's denote the cost of the ball as x dollars."},
                                {"type": "thinking", "thinking": []},
                                {"type": "text", "text": "Therefore, the ball costs $0.05."}
                            ]
                        }
                    }],
                    "usage": {"prompt_tokens": 89, "completion_tokens": 330, "total_tokens": 419, "num_cached_tokens": 0}
                }
                """;

        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(jsonResponse)
                .build());

        ChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName(MistralAiChatModelName.MAGISTRAL_MEDIUM_LATEST)
                .returnThinking(true)
                .build();

        // when
        ChatResponse response = model.chat(UserMessage.from("How much does the ball cost?"));

        // then - text blocks concatenated, empty thinking array results in null
        assertThat(response.aiMessage().text())
                .isEqualTo("Let's denote the cost of the ball as x dollars.\nTherefore, the ball costs $0.05.");
        assertThat(response.aiMessage().thinking()).isNull();
    }

    @Test
    void should_handle_thinking_with_tool_calls() {
        // given - real Magistral response: thinking content with tool calls
        String jsonResponse =
                """
                {
                    "id": "b39b103ae4d14d11ac1ad1a8c4e2f8f5",
                    "created": 1769417132,
                    "model": "magistral-medium-latest",
                    "choices": [{
                        "index": 0,
                        "finish_reason": "tool_calls",
                        "message": {
                            "role": "assistant",
                            "tool_calls": [{
                                "id": "ggLXfzi8o",
                                "type": "function",
                                "function": {
                                    "name": "getWeather",
                                    "arguments": "{\\"city\\": \\"Munich\\"}"
                                }
                            }],
                            "content": [{
                                "type": "thinking",
                                "thinking": [{
                                    "type": "text",
                                    "text": "The user wants to know the weather in Munich. I need to call the getWeather function."
                                }]
                            }]
                        }
                    }],
                    "usage": {"prompt_tokens": 100, "completion_tokens": 125, "total_tokens": 225, "num_cached_tokens": 0}
                }
                """;

        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(jsonResponse)
                .build());

        ChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName(MistralAiChatModelName.MAGISTRAL_MEDIUM_LATEST)
                .returnThinking(true)
                .build();

        // when
        ChatResponse response = model.chat(UserMessage.from("What is the weather in Munich?"));

        // then
        assertThat(response.aiMessage().thinking())
                .isEqualTo("The user wants to know the weather in Munich. I need to call the getWeather function.");
        assertThat(response.aiMessage().toolExecutionRequests()).hasSize(1);
        assertThat(response.aiMessage().toolExecutionRequests().get(0).id()).isEqualTo("ggLXfzi8o");
        assertThat(response.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("getWeather");
        assertThat(response.aiMessage().toolExecutionRequests().get(0).arguments())
                .isEqualTo("{\"city\": \"Munich\"}");
    }

    @Test
    void should_handle_response_without_thinking() {
        // given - response with only text content, no thinking
        String jsonResponse =
                """
                {
                    "id": "abc123",
                    "created": 1769421662,
                    "model": "mistral-large-latest",
                    "choices": [{
                        "index": 0,
                        "finish_reason": "stop",
                        "message": {
                            "role": "assistant",
                            "content": [
                                {"type": "text", "text": "The answer is 42."}
                            ]
                        }
                    }],
                    "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30, "num_cached_tokens": 0}
                }
                """;

        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(jsonResponse)
                .build());

        ChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName(MistralAiChatModelName.MISTRAL_LARGE_LATEST)
                .returnThinking(true)
                .build();

        // when
        ChatResponse response = model.chat(UserMessage.from("What is the answer?"));

        // then - thinking should be null when not present in response
        assertThat(response.aiMessage().text()).isEqualTo("The answer is 42.");
        assertThat(response.aiMessage().thinking()).isNull();
    }

    @Test
    void should_concatenate_multiple_thinking_blocks() {
        // given - response with multiple text blocks inside thinking
        String jsonResponse =
                """
                {
                    "id": "abc123",
                    "created": 1769421662,
                    "model": "magistral-medium-latest",
                    "choices": [{
                        "index": 0,
                        "finish_reason": "stop",
                        "message": {
                            "role": "assistant",
                            "content": [
                                {"type": "thinking", "thinking": [
                                    {"type": "text", "text": "First thought..."},
                                    {"type": "text", "text": "Second thought..."}
                                ]},
                                {"type": "text", "text": "The answer is 42."}
                            ]
                        }
                    }],
                    "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30, "num_cached_tokens": 0}
                }
                """;

        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(jsonResponse)
                .build());

        ChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName(MistralAiChatModelName.MAGISTRAL_MEDIUM_LATEST)
                .returnThinking(true)
                .build();

        // when
        ChatResponse response = model.chat(UserMessage.from("What is the answer?"));

        // then - multiple thinking blocks should be concatenated with newline
        assertThat(response.aiMessage().text()).isEqualTo("The answer is 42.");
        assertThat(response.aiMessage().thinking()).isEqualTo("First thought...\nSecond thought...");
    }

    @Test
    void should_NOT_return_thinking_with_tool_calls_when_returnThinking_is_false() {
        // given - thinking content with tool calls, but returnThinking is false
        String jsonResponse =
                """
                {
                    "id": "b39b103ae4d14d11ac1ad1a8c4e2f8f5",
                    "created": 1769417132,
                    "model": "magistral-medium-latest",
                    "choices": [{
                        "index": 0,
                        "finish_reason": "tool_calls",
                        "message": {
                            "role": "assistant",
                            "tool_calls": [{
                                "id": "ggLXfzi8o",
                                "type": "function",
                                "function": {
                                    "name": "getWeather",
                                    "arguments": "{\\"city\\": \\"Munich\\"}"
                                }
                            }],
                            "content": [{
                                "type": "thinking",
                                "thinking": [{
                                    "type": "text",
                                    "text": "The user wants to know the weather in Munich."
                                }]
                            }]
                        }
                    }],
                    "usage": {"prompt_tokens": 100, "completion_tokens": 125, "total_tokens": 225, "num_cached_tokens": 0}
                }
                """;

        MockHttpClient mockHttpClient = MockHttpClient.thatAlwaysResponds(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(jsonResponse)
                .build());

        ChatModel model = MistralAiChatModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mockHttpClient))
                .apiKey("test-api-key")
                .modelName(MistralAiChatModelName.MAGISTRAL_MEDIUM_LATEST)
                .returnThinking(false)
                .build();

        // when
        ChatResponse response = model.chat(UserMessage.from("What is the weather in Munich?"));

        // then - thinking should be null, but tool calls should still be present
        assertThat(response.aiMessage().thinking()).isNull();
        assertThat(response.aiMessage().toolExecutionRequests()).hasSize(1);
        assertThat(response.aiMessage().toolExecutionRequests().get(0).id()).isEqualTo("ggLXfzi8o");
        assertThat(response.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("getWeather");
    }
}
