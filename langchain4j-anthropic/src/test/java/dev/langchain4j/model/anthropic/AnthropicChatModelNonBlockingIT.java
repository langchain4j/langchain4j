package dev.langchain4j.model.anthropic;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.reactive.streaming.AbstractChatModelNonBlockingIT;

/**
 * Anthropic binding of the shared non-blocking chat-model TCK ({@link AbstractChatModelNonBlockingIT}). Anthropic
 * streams over the JDK {@code HttpClient} transport's reactive {@code stream()}, so responses are parsed and
 * dispatched on its workers ({@code HttpClient-*}).
 */
class AnthropicChatModelNonBlockingIT extends AbstractChatModelNonBlockingIT {

    @Override
    protected ChatModel syncModel(String baseUrl, boolean logging) {
        return AnthropicChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey("dummy-key")
                .modelName(AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001)
                .maxTokens(20)
                .logRequests(logging)
                .logResponses(logging)
                .build();
    }

    @Override
    protected StreamingChatModel streamingModel(String baseUrl, boolean logging) {
        return AnthropicStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey("dummy-key")
                .modelName(AnthropicChatModelName.CLAUDE_HAIKU_4_5_20251001)
                .maxTokens(200)
                .logRequests(logging)
                .logResponses(logging)
                .build();
    }

    @Override
    protected String nonStreamingResponseBody() {
        return """
                {
                  "id": "msg_123",
                  "type": "message",
                  "role": "assistant",
                  "model": "claude-haiku-4-5-20251001",
                  "content": [{"type": "text", "text": "Berlin"}],
                  "stop_reason": "end_turn",
                  "usage": {"input_tokens": 10, "output_tokens": 5}
                }
                """;
    }

    @Override
    protected String streamingResponseBody() {
        return """
                event: message_start
                data: {"type":"message_start","message":{"id":"msg_1","type":"message","role":"assistant","content":[],"model":"claude-haiku-4-5-20251001","stop_reason":null,"usage":{"input_tokens":5,"output_tokens":0}}}

                event: content_block_start
                data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi"}}

                event: content_block_delta
                data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" there"}}

                event: content_block_stop
                data: {"type":"content_block_stop","index":0}

                event: message_delta
                data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":2}}

                event: message_stop
                data: {"type":"message_stop"}

                """;
    }
}
