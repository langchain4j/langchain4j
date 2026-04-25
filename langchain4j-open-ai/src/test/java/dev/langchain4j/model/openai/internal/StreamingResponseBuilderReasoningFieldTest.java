package dev.langchain4j.model.openai.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingResponseBuilder;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionChoice;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.chat.Delta;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;

/**
 * Regression test for issue #4796: VLLM deprecated the 'reasoning_content' field
 * in favor of 'reasoning'. This test verifies that the streaming response builder
 * correctly extracts thinking content from the 'reasoning' field.
 */
class StreamingResponseBuilderReasoningFieldTest {

    @Test
    void should_extract_reasoning_from_new_reasoning_field() {

        // given - VLLM-style response using the new "reasoning" field (not "reasoning_content")
        OpenAiStreamingResponseBuilder builder = new OpenAiStreamingResponseBuilder(true);

        // Simulate streaming chunks with the new "reasoning" field
        ChatCompletionResponse chunk1 = createChunk(null, "First reasoning step");
        ChatCompletionResponse chunk2 = createChunk(null, ", then the next step");
        ChatCompletionResponse chunk3 = createChunk("The answer is ", null);
        ChatCompletionResponse chunk4 = createChunk("42", null);

        // when
        builder.append(chunk1);
        builder.append(chunk2);
        builder.append(chunk3);
        builder.append(chunk4);

        var response = builder.build();

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isEqualTo("The answer is 42");
        assertThat(aiMessage.thinking()).isEqualTo("First reasoning step, then the next step");
    }

    @Test
    void should_extract_reasoning_from_legacy_reasoning_content_field() {

        // given - legacy response using the old "reasoning_content" field
        OpenAiStreamingResponseBuilder builder = new OpenAiStreamingResponseBuilder(true);

        ChatCompletionResponse chunk1 = createLegacyChunk(null, "Legacy thinking part 1");
        ChatCompletionResponse chunk2 = createLegacyChunk(null, " and part 2");
        ChatCompletionResponse chunk3 = createLegacyChunk("Final answer", null);

        // when
        builder.append(chunk1);
        builder.append(chunk2);
        builder.append(chunk3);

        var response = builder.build();

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isEqualTo("Final answer");
        assertThat(aiMessage.thinking()).isEqualTo("Legacy thinking part 1 and part 2");
    }

    @Test
    void should_prefer_reasoning_over_reasoning_content_when_both_present() {

        // given - response with both fields present (should prefer "reasoning")
        OpenAiStreamingResponseBuilder builder = new OpenAiStreamingResponseBuilder(true);

        // Create a chunk where both fields are populated
        String bothFieldsJson = """
                {
                    "id": "test-both-fields",
                    "object": "chat.completion.chunk",
                    "created": 1234567890,
                    "model": "vllm-model",
                    "choices": [
                        {
                            "index": 0,
                            "delta": {
                                "content": "Answer",
                                "reasoning": "Preferred reasoning",
                                "reasoning_content": "Legacy reasoning"
                            },
                            "finish_reason": "stop"
                        }
                    ]
                }
                """;

        ChatCompletionResponse chunk = Json.fromJson(bothFieldsJson, ChatCompletionResponse.class);

        // when
        builder.append(chunk);

        var response = builder.build();

        // then - should use "reasoning" field content
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isEqualTo("Answer");
        assertThat(aiMessage.thinking()).isEqualTo("Preferred reasoning");
    }

    @Test
    void should_fire_onPartialThinking_callback_for_reasoning_field() {

        // given
        OpenAiStreamingResponseBuilder builder = new OpenAiStreamingResponseBuilder(true);
        TestStreamingChatResponseHandler spyHandler = spy(new TestStreamingChatResponseHandler());

        // Simulate streaming with reasoning content
        ChatCompletionResponse chunk1 = createChunk(null, "Thinking starts here");
        ChatCompletionResponse chunk2 = createChunk("Response content", ", continues");

        // when
        builder.append(chunk1);
        builder.append(chunk2);

        // Access the thinking that was accumulated
        var response = builder.build();
        AiMessage aiMessage = response.aiMessage();

        // Verify the reasoning was correctly accumulated
        assertThat(aiMessage.thinking()).isEqualTo("Thinking starts here, continues");

        // Verify content was also accumulated
        assertThat(aiMessage.text()).isEqualTo("Response content");
    }

    @Test
    void should_not_accumulate_reasoning_when_return_thinking_is_false() {

        // given
        OpenAiStreamingResponseBuilder builder = new OpenAiStreamingResponseBuilder(false);
        TestStreamingChatResponseHandler spyHandler = spy(new TestStreamingChatResponseHandler());

        ChatCompletionResponse chunk1 = createChunk(null, "This thinking should be ignored");
        ChatCompletionResponse chunk2 = createChunk("Just the response", null);

        // when
        builder.append(chunk1);
        builder.append(chunk2);

        var response = builder.build();

        // then - reasoning should be null when returnThinking is false
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isEqualTo("Just the response");
        assertThat(aiMessage.thinking()).isNull();
    }

    /**
     * Creates a chunk using the new VLLM "reasoning" field.
     */
    private ChatCompletionResponse createChunk(String content, String reasoning) {
        String json = """
                {
                    "id": "test-chunk",
                    "object": "chat.completion.chunk",
                    "created": 1234567890,
                    "model": "vllm-model",
                    "choices": [
                        {
                            "index": 0,
                            "delta": {
                                "content": %s,
                                "reasoning": %s
                            },
                            "finish_reason": null
                        }
                    ]
                }
                """.formatted(
                content != null ? "\"" + content + "\"" : "null",
                reasoning != null ? "\"" + reasoning + "\"" : "null"
        );
        return Json.fromJson(json, ChatCompletionResponse.class);
    }

    /**
     * Creates a chunk using the legacy "reasoning_content" field.
     */
    private ChatCompletionResponse createLegacyChunk(String content, String reasoningContent) {
        String json = """
                {
                    "id": "test-legacy-chunk",
                    "object": "chat.completion.chunk",
                    "created": 1234567890,
                    "model": "openai-model",
                    "choices": [
                        {
                            "index": 0,
                            "delta": {
                                "content": %s,
                                "reasoning_content": %s
                            },
                            "finish_reason": null
                        }
                    ]
                }
                """.formatted(
                content != null ? "\"" + content + "\"" : "null",
                reasoningContent != null ? "\"" + reasoningContent + "\"" : "null"
        );
        return Json.fromJson(json, ChatCompletionResponse.class);
    }
}
