package dev.langchain4j.model.openai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.openai.internal.chat.ChatCompletionChoice;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
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
}
