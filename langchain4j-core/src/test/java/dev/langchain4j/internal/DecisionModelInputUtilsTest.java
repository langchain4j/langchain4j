package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionModelInputUtilsTest {

    @Test
    void should_keep_only_user_and_assistant_messages_with_text() {

        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("1")
                .name("weather")
                .arguments("{}")
                .build();

        List<Map<String, String>> messages = DecisionModelInputUtils.messages(List.of(
                SystemMessage.from("You are a helpful assistant"),
                UserMessage.from("Weather in Berlin?"),
                AiMessage.from(toolCall),
                ToolExecutionResultMessage.from(toolCall, "{\"temperature\": 20}"),
                AiMessage.from("It is 20 degrees"),
                CustomMessage.from(Map.of("key", "value")),
                UserMessage.from("And in Paris?")));

        assertThat(messages)
                .containsExactly(
                        Map.of("role", "user", "text", "Weather in Berlin?"),
                        Map.of("role", "assistant", "text", "It is 20 degrees"),
                        Map.of("role", "user", "text", "And in Paris?"));
    }
}
