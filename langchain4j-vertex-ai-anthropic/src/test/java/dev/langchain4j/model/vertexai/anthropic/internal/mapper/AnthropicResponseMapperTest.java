package dev.langchain4j.model.vertexai.anthropic.internal.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.vertexai.anthropic.internal.api.AnthropicResponse;
import dev.langchain4j.model.vertexai.anthropic.internal.client.VertexAiAnthropicJsonUtils;
import org.junit.jupiter.api.Test;

class AnthropicResponseMapperTest {

    @Test
    void should_ignore_thinking_blocks_when_extracting_text() {
        AnthropicResponse response = VertexAiAnthropicJsonUtils.fromJson("""
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":[
                   {"type":"thinking","thinking":"Let me work this out.","signature":"sig_1"},
                   {"type":"redacted_thinking","data":"encrypted"},
                   {"type":"text","text":"The answer is 42."}],
                 "stop_reason":"end_turn",
                 "usage":{"input_tokens":10,"output_tokens":5}}""", AnthropicResponse.class);

        ChatResponse chatResponse = AnthropicResponseMapper.toChatResponse(response);

        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).isEqualTo("The answer is 42.");
        assertThat(aiMessage.thinking()).isNull();
        assertThat(aiMessage.hasToolExecutionRequests()).isFalse();
        assertThat(chatResponse.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_ignore_thinking_blocks_next_to_tool_use() {
        AnthropicResponse response = VertexAiAnthropicJsonUtils.fromJson("""
                {"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
                 "content":[
                   {"type":"thinking","thinking":"","signature":"sig_1"},
                   {"type":"tool_use","id":"toolu_1","name":"getWeather","input":{"city":"Paris"}}],
                 "stop_reason":"tool_use",
                 "usage":{"input_tokens":10,"output_tokens":5}}""", AnthropicResponse.class);

        AiMessage aiMessage = AnthropicResponseMapper.toAiMessage(response);

        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        assertThat(aiMessage.toolExecutionRequests().get(0).name()).isEqualTo("getWeather");
        assertThat(aiMessage.toolExecutionRequests().get(0).arguments()).isEqualTo("{\"city\":\"Paris\"}");
    }
}
