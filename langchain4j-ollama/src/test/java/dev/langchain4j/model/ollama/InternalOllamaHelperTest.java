package dev.langchain4j.model.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InternalOllamaHelperTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.databind.module.SimpleModule()
                    .addSerializer(Role.class, new com.fasterxml.jackson.databind.JsonSerializer<Role>() {
                        @Override
                        public void serialize(Role role, com.fasterxml.jackson.core.JsonGenerator gen,
                                com.fasterxml.jackson.databind.SerializerProvider serializers) throws java.io.IOException {
                            gen.writeString(role.name().toLowerCase(java.util.Locale.ROOT));
                        }
                    }));

    @Test
    void toToolExecutionRequests_mapsToolCalls() {
        ToolCall toolCall = ToolCall.builder()
                .id("tool-1")
                .function(FunctionCall.builder()
                        .name("lookupWeather")
                        .arguments(Map.of("city", "Shanghai"))
                        .build())
                .build();

        List<ToolExecutionRequest> result = InternalOllamaHelper.toToolExecutionRequests(List.of(toolCall));

        assertThat(result)
                .containsExactly(ToolExecutionRequest.builder()
                        .id("tool-1")
                        .name("lookupWeather")
                        .arguments("{\"city\":\"Shanghai\"}")
                        .build());
    }

    @Test
    void toToolExecutionRequests_handlesEmptyToolCalls() {
        List<ToolExecutionRequest> result = InternalOllamaHelper.toToolExecutionRequests(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void toOllamaMessages_includesToolNameAndIsError_onToolExecutionResultMessage() throws Exception {
        ToolExecutionResultMessage toolResult = ToolExecutionResultMessage.builder()
                .id("call_abc123")
                .toolName("getWeather")
                .text("Sunny, 22°C")
                .isError(false)
                .build();

        List<ChatMessage> messages = List.of(toolResult);
        List<Message> ollamaMessages = InternalOllamaHelper.toOllamaMessages(messages);

        assertThat(ollamaMessages).hasSize(1);
        Message msg = ollamaMessages.get(0);
        assertThat(msg.getRole()).isEqualTo(Role.TOOL);
        assertThat(msg.getContent()).isEqualTo("Sunny, 22°C");
        assertThat(msg.getToolName()).isEqualTo("getWeather");
        assertThat(msg.getIsError()).isEqualTo(false);

        // Verify JSON serialization includes tool_name and is_error
        String json = OBJECT_MAPPER.writeValueAsString(msg);
        assertThat(json).contains("\"tool_name\"");
        assertThat(json).contains("\"is_error\"");
    }

    @Test
    void toOllamaMessages_includesIsErrorTrue_onToolExecutionResultMessage() throws Exception {
        ToolExecutionResultMessage toolResult = ToolExecutionResultMessage.builder()
                .id("call_def456")
                .toolName("getWeather")
                .text("Error: location not found")
                .isError(true)
                .build();

        List<ChatMessage> messages = List.of(toolResult);
        List<Message> ollamaMessages = InternalOllamaHelper.toOllamaMessages(messages);

        assertThat(ollamaMessages).hasSize(1);
        Message msg = ollamaMessages.get(0);
        assertThat(msg.getRole()).isEqualTo(Role.TOOL);
        assertThat(msg.getToolName()).isEqualTo("getWeather");
        assertThat(msg.getIsError()).isEqualTo(true);
    }
}
