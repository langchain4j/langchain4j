package dev.langchain4j.model.mistralai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

class MistralAiChatModelToolCallsTest {

    /**
     * OpenAI-compatible servers (vLLM, llama.cpp, …) often return {@code message.content = null}
     * when the assistant emits only tool calls. This test pins that response shape so the mapper
     * never reintroduces an NPE on null content.
     */
    @Test
    void should_handle_response_with_only_tool_calls_and_null_content() {
        // given - vLLM-style response: assistant emits only tool_calls, content is null
        String jsonResponse = """
                {
                    "id": "chatcmpl-9ccb8be3370654bc",
                    "object": "chat.completion",
                    "created": 1778075620,
                    "model": "mistralai/Ministral-3-8B-Instruct-2512",
                    "choices": [{
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": null,
                            "tool_calls": [{
                                "id": "chatcmpl-tool-b51c242ff1eacb66",
                                "type": "function",
                                "function": {
                                    "name": "verifier_date_incident",
                                    "arguments": "{\\"date\\": \\"2026-05-06\\"}"
                                }
                            }]
                        },
                        "finish_reason": "tool_calls"
                    }],
                    "usage": {"prompt_tokens": 4175, "completion_tokens": 23, "total_tokens": 4198}
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

        ToolSpecification verifierDateIncident = ToolSpecification.builder()
                .name("verifier_date_incident")
                .description("Vérifie la date d'un incident")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("date")
                        .required("date")
                        .build())
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("Vérifie la date d'incident pour aujourd'hui."))
                .toolSpecifications(verifierDateIncident)
                .build();

        // when
        ChatResponse response = model.chat(chatRequest);

        // then - no NPE; tool call is parsed; text is empty (no text content was returned)
        assertThat(response.aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(response.aiMessage().toolExecutionRequests()).hasSize(1);
        assertThat(response.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("verifier_date_incident");
        assertThat(response.aiMessage().toolExecutionRequests().get(0).arguments())
                .contains("2026-05-06");
        assertThat(response.aiMessage().text()).isEmpty();
    }

    /**
     * Sanity: when {@code content} carries text alongside {@code tool_calls}, both must be
     * exposed on the {@link dev.langchain4j.data.message.AiMessage}.
     */
    @Test
    void should_handle_response_with_both_text_content_and_tool_calls() {
        // given
        String jsonResponse = """
                {
                    "id": "chatcmpl-mixed",
                    "created": 1778075620,
                    "model": "mistralai/Ministral-3-8B-Instruct-2512",
                    "choices": [{
                        "index": 0,
                        "message": {
                            "role": "assistant",
                            "content": [{"type": "text", "text": "Je vérifie cela tout de suite."}],
                            "tool_calls": [{
                                "id": "call_1",
                                "type": "function",
                                "function": {
                                    "name": "verifier_date_incident",
                                    "arguments": "{\\"date\\": \\"2026-05-06\\"}"
                                }
                            }]
                        },
                        "finish_reason": "tool_calls"
                    }],
                    "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30}
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
        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(UserMessage.from("Vérifie la date."))
                .toolSpecifications(ToolSpecification.builder()
                        .name("verifier_date_incident")
                        .parameters(JsonObjectSchema.builder()
                                .addStringProperty("date")
                                .required("date")
                                .build())
                        .build())
                .build());

        // then
        assertThat(response.aiMessage().text()).isEqualTo("Je vérifie cela tout de suite.");
        assertThat(response.aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(response.aiMessage().toolExecutionRequests()).hasSize(1);
        assertThat(response.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("verifier_date_incident");
    }

    /**
     * Regression guard: text-only response (no tool calls) must continue to work — confirms
     * the null-guard added for tool-call-only responses doesn't disturb the regular path.
     */
    @Test
    void should_handle_response_with_text_only_and_no_tool_calls() {
        // given
        String jsonResponse = """
                {
                    "id": "chatcmpl-textonly",
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
