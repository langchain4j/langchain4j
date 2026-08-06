package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BedrockBatchConverseMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void builds_converse_model_input_for_text_and_inference_config() throws Exception {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Say hello in one word."))
                .parameters(DefaultChatRequestParameters.builder()
                        .maxOutputTokens(16)
                        .temperature(0.2)
                        .build())
                .build();

        String json = MAPPER.writeValueAsString(BedrockBatchConverseMapper.toModelInput(request));

        assertThat(json)
                .isEqualTo("{\"messages\":[{\"role\":\"user\",\"content\":[{\"text\":\"Say hello in one word.\"}]}],"
                        + "\"inferenceConfig\":{\"maxTokens\":16,\"temperature\":0.2}}");
    }

    @Test
    void system_messages_go_into_the_system_field() {
        ChatRequest request = ChatRequest.builder()
                .messages(SystemMessage.from("You are terse."), UserMessage.from("Hi"))
                .build();

        Map<String, Object> modelInput = BedrockBatchConverseMapper.toModelInput(request);

        assertThat(modelInput).containsKey("system");
        assertThat(modelInput.get("system")).isEqualTo(java.util.List.of(Map.of("text", "You are terse.")));
        assertThat(modelInput).doesNotContainKey("inferenceConfig");
    }

    @Test
    void parses_converse_model_output_into_chat_response() throws Exception {
        String modelOutput = "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"Hello!\"}]}},"
                + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":5,\"outputTokens\":2,\"totalTokens\":7}}";

        ChatResponse response = BedrockBatchConverseMapper.toChatResponse(MAPPER.readTree(modelOutput), "some-model");

        assertThat(response.aiMessage().text()).isEqualTo("Hello!");
        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.metadata().tokenUsage().inputTokenCount()).isEqualTo(5);
        assertThat(response.metadata().tokenUsage().outputTokenCount()).isEqualTo(2);
        assertThat(response.metadata().modelName()).isEqualTo("some-model");
    }

    @Test
    void joins_multiple_text_blocks_with_blank_line_like_bedrock_chat_model() throws Exception {
        String modelOutput = "{\"output\":{\"message\":{\"content\":"
                + "[{\"text\":\"first\"},{\"reasoningContent\":{\"reasoningText\":{\"text\":\"ignored\"}}},"
                + "{\"text\":\"second\"}]}},\"stopReason\":\"end_turn\"}";

        ChatResponse response = BedrockBatchConverseMapper.toChatResponse(MAPPER.readTree(modelOutput), "m");

        assertThat(response.aiMessage().text()).isEqualTo("first\n\nsecond");
    }

    @Test
    void maps_max_tokens_stop_reason_to_length() throws Exception {
        String modelOutput =
                "{\"output\":{\"message\":{\"content\":[{\"text\":\"x\"}]}},\"stopReason\":\"max_tokens\"}";

        ChatResponse response = BedrockBatchConverseMapper.toChatResponse(MAPPER.readTree(modelOutput), "m");

        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.LENGTH);
    }
}
