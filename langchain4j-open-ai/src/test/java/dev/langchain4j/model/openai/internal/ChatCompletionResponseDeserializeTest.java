package dev.langchain4j.model.openai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.openai.internal.chat.AssistantMessage;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionChoice;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.chat.Delta;
import dev.langchain4j.model.openai.internal.chat.ToolCall;
import org.junit.jupiter.api.Test;

class ChatCompletionResponseDeserializeTest {

    @Test
    void should_deserialize_chat_response_without_tool_type() {

        // given
        String json = """
                {
                    "id": "0195a749b17b5668b9753240788da6f8",
                    "object": "chat.completion.chunk",
                    "created": 1742268380,
                    "model": "deepseek-ai/DeepSeek-V3",
                    "choices": [
                        {
                            "index": 0,
                            "delta": {
                                "content": null,
                                "reasoning_content": null,
                                "tool_calls": [
                                    {
                                        "index": 0,
                                        "id": "",
                                        "type": "",
                                        "function": {
                                            "arguments": "{\\""
                                        }
                                    }
                                ]
                            },
                            "finish_reason": null
                        }
                    ],
                    "system_fingerprint": "",
                    "usage": {
                        "prompt_tokens": 83,
                        "completion_tokens": 2,
                        "total_tokens": 85
                    }
                }
                """;

        // when
        ChatCompletionResponse response = Json.fromJson(json, ChatCompletionResponse.class);

        // then
        ChatCompletionChoice chatCompletionChoice = response.choices().get(0);
        ToolCall toolCall = chatCompletionChoice.delta().toolCalls().get(0);
        assertThat(toolCall.function().arguments()).isEqualTo("{\"");
    }

    @Test
    void should_deserialize_message_with_reasoning_content_field() {

        // given
        String json = """
                {
                    "id": "chatcmpl-123",
                    "object": "chat.completion",
                    "created": 1742268380,
                    "model": "deepseek-reasoner",
                    "choices": [
                        {
                            "index": 0,
                            "message": {
                                "role": "assistant",
                                "content": "4",
                                "reasoning_content": "2 + 2 = 4"
                            },
                            "finish_reason": "stop"
                        }
                    ]
                }
                """;

        // when
        ChatCompletionResponse response = Json.fromJson(json, ChatCompletionResponse.class);

        // then
        AssistantMessage message = response.choices().get(0).message();
        assertThat(message.content()).isEqualTo("4");
        assertThat(message.reasoningContent()).isEqualTo("2 + 2 = 4");
    }

    @Test
    void should_deserialize_message_with_reasoning_field() {

        // given
        // vLLM >= 0.10 and OpenRouter return the reasoning text in a "reasoning" field
        // instead of "reasoning_content": https://github.com/langchain4j/langchain4j/issues/4796
        String json = """
                {
                    "id": "chatcmpl-123",
                    "object": "chat.completion",
                    "created": 1742268380,
                    "model": "qwen3",
                    "choices": [
                        {
                            "index": 0,
                            "message": {
                                "role": "assistant",
                                "content": "4",
                                "reasoning": "2 + 2 = 4"
                            },
                            "finish_reason": "stop"
                        }
                    ]
                }
                """;

        // when
        ChatCompletionResponse response = Json.fromJson(json, ChatCompletionResponse.class);

        // then
        AssistantMessage message = response.choices().get(0).message();
        assertThat(message.content()).isEqualTo("4");
        assertThat(message.reasoningContent()).isEqualTo("2 + 2 = 4");
    }

    @Test
    void should_deserialize_delta_with_reasoning_field() {

        // given
        String json = """
                {
                    "id": "chatcmpl-123",
                    "object": "chat.completion.chunk",
                    "created": 1742268380,
                    "model": "qwen3",
                    "choices": [
                        {
                            "index": 0,
                            "delta": {
                                "content": null,
                                "reasoning": "2 + 2"
                            },
                            "finish_reason": null
                        }
                    ]
                }
                """;

        // when
        ChatCompletionResponse response = Json.fromJson(json, ChatCompletionResponse.class);

        // then
        Delta delta = response.choices().get(0).delta();
        assertThat(delta.content()).isNull();
        assertThat(delta.reasoningContent()).isEqualTo("2 + 2");
    }

    @Test
    void should_deserialize_message_without_reasoning_fields() {

        // given
        String json = """
                {
                    "id": "chatcmpl-123",
                    "object": "chat.completion",
                    "created": 1742268380,
                    "model": "gpt-4o-mini",
                    "choices": [
                        {
                            "index": 0,
                            "message": {
                                "role": "assistant",
                                "content": "4"
                            },
                            "finish_reason": "stop"
                        }
                    ]
                }
                """;

        // when
        ChatCompletionResponse response = Json.fromJson(json, ChatCompletionResponse.class);

        // then
        AssistantMessage message = response.choices().get(0).message();
        assertThat(message.content()).isEqualTo("4");
        assertThat(message.reasoningContent()).isNull();
    }
}
