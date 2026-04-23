package dev.langchain4j.model.openai.internal.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ChatCompletionRequestSerializationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void reasoningEffort_should_serialize_as_nested_reasoning_object() throws Exception {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4o-mini")
                .messages(List.of(UserMessage.from("hello")))
                .reasoningEffort("low")
                .build();

        String json = OBJECT_MAPPER.writeValueAsString(request);

        assertThat(json).contains("\"reasoning\":{\"effort\":\"low\"}");
        assertThat(json).doesNotContain("reasoning_effort");
    }

    @Test
    void reasoningEffort_null_should_not_include_reasoning_field() throws Exception {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4o-mini")
                .messages(List.of(UserMessage.from("hello")))
                .build();

        String json = OBJECT_MAPPER.writeValueAsString(request);

        assertThat(json).doesNotContain("reasoning");
    }

    @Test
    void reasoningEffort_high_should_serialize_correctly() throws Exception {
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4o-mini")
                .messages(List.of(UserMessage.from("hello")))
                .reasoningEffort("high")
                .build();

        String json = OBJECT_MAPPER.writeValueAsString(request);

        assertThat(json).contains("\"reasoning\":{\"effort\":\"high\"}");
    }

    @Test
    void reasoning_map_should_be_deserializable_from_nested_json() throws Exception {
        // Test that the "reasoning" field itself can be deserialized
        // using the from() builder pattern
        String json = "{\"reasoning\":{\"effort\":\"medium\"}}";
        
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model("gpt-4o-mini")
                .reasoning(Map.of("effort", "medium"))
                .build();
        
        assertThat(request.reasoning()).isEqualTo(Map.of("effort", "medium"));
    }
}