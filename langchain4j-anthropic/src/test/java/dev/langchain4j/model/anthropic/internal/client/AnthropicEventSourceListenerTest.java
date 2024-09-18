package dev.langchain4j.model.anthropic.internal.client;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.TestStreamingResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnthropicEventSourceListenerTest {

    @Test
    void should_have_text() {
        // given
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        AnthropicEventSourceListener listener = new AnthropicEventSourceListener(handler, false);

        // when
        listener.onEvent(null, null, "message_start", "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_018srTqYKTYLn7w1ZewrHoik\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-3-5-sonnet-20240620\",\"content\":[],\"stop_reason\":null,\"stop_sequence\":null,\"usage\":{\"input_tokens\":451,\"output_tokens\":5}}           }");
        listener.onEvent(null, null, "content_block_start", "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}        }");
        listener.onEvent(null, null, "ping", "{\"type\": \"ping\"}");
        listener.onEvent(null, null, "content_block_delta", "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"42\"}   }");
        listener.onEvent(null, null, "content_block_stop", "{\"type\":\"content_block_stop\",\"index\":0}");
        listener.onEvent(null, null, "message_delta", "{\"type\":\"content_block_stop\",\"index\":0}");
        listener.onEvent(null, null, "message_delta", "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":18}}");
        listener.onEvent(null, null, "message_stop", "{\"type\":\"message_stop\"         }");

        // then
        Response<AiMessage> response = handler.get();
        AiMessage aiMessage = response.content();
        assertEquals("42", aiMessage.text());
        assertEquals(FinishReason.STOP, response.finishReason());

        TokenUsage tokenUsage = response.tokenUsage();
        assertEquals(451, tokenUsage.inputTokenCount());
        assertEquals(23, tokenUsage.outputTokenCount());
        assertEquals(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount(), tokenUsage.totalTokenCount());
    }

    @Test
    void should_have_tool_execution_request() {
        TestStreamingResponseHandler<AiMessage> handler = new TestStreamingResponseHandler<>();
        AnthropicEventSourceListener listener = new AnthropicEventSourceListener(handler, false);

        // when
        listener.onEvent(null, null, "message_start", "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_018srTqYKTYLn7w1ZewrHoik\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-3-5-sonnet-20240620\",\"content\":[],\"stop_reason\":null,\"stop_sequence\":null,\"usage\":{\"input_tokens\":451,\"output_tokens\":5}}  }");
        listener.onEvent(null, null, "content_block_start", "{\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"tool_use\",\"id\":\"toolu_013nghsbr7CcMyYYFE4CFzUQ\",\"name\":\"calculator\",\"input\":{}} }");
        listener.onEvent(null, null, "ping", "{\"type\": \"ping\"}");
        listener.onEvent(null, null, "content_block_delta", "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"first\\\": 2,\"}   }");
        listener.onEvent(null, null, "content_block_delta", "{\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\" \\\"second\\\": 2}\"}  }");
        listener.onEvent(null, null, "content_block_stop", "{\"type\":\"content_block_stop\",\"index\":0}");

        listener.onEvent(null, null, "message_delta", "{\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"tool_use\",\"stop_sequence\":null},\"usage\":{\"output_tokens\":68}}");
        listener.onEvent(null, null, "message_stop", "{\"type\":\"message_stop\"         }");

        // then
        Response<AiMessage> response = handler.get();
        AiMessage aiMessage = response.content();
        assertTrue(aiMessage.hasToolExecutionRequests());
        Assertions.assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        assertEquals(FinishReason.TOOL_EXECUTION, response.finishReason());

        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertEquals("toolu_013nghsbr7CcMyYYFE4CFzUQ", toolExecutionRequest.id());
        assertEquals("calculator", toolExecutionRequest.name());
        assertEquals("{\"first\": 2, \"second\": 2}", toolExecutionRequest.arguments());
    }
}