package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.common.AbstractStreamingChatModelIT;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockStreamingChatModelWithConverseIT extends AbstractStreamingChatModelIT {

    @Override
    protected List<StreamingChatLanguageModel> models() {
        return List.of(
                TestedModelsWithConverseAPI.STREAMING_AWS_NOVA_MICRO,
                TestedModelsWithConverseAPI.STREAMING_AWS_NOVA_LITE,
                TestedModelsWithConverseAPI.STREAMING_AWS_NOVA_PRO,
                TestedModelsWithConverseAPI.STREAMING_AI_JAMBA_1_5_MINI,
                TestedModelsWithConverseAPI.STREAMING_CLAUDE_3_HAIKU,
                TestedModelsWithConverseAPI.STREAMING_COHERE_COMMAND_R_PLUS,
                TestedModelsWithConverseAPI.STREAMING_MISTRAL_LARGE);
    }

    @Test
    void should_reason() {
        // given
        StreamingChatLanguageModel model = BedrockStreamingChatModel.builder()
                .modelId("us.anthropic.claude-3-7-sonnet-20250219-v1:0")
                .defaultRequestParameters(BedrockChatRequestParameters.builder()
                        .enableReasoning(1024L)
                        .build())
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany? "))
                .build();

        // when
        ChatResponse chatResponse = chat(model, chatRequest).chatResponse();

        // then
        AiMessage aiMessage = chatResponse.aiMessage();
        assertThat(aiMessage.text()).containsIgnoringWhitespaces("Berlin");
    }

    @Test
    void should_fail_if_reasoning_enabled() {
        // given
        StreamingChatLanguageModel model = BedrockStreamingChatModel.builder()
                .modelId("us.amazon.nova-lite-v1:0")
                .defaultRequestParameters(BedrockChatRequestParameters.builder()
                        .enableReasoning(1024L)
                        .build())
                .build();

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("What is the capital of Germany? "))
                .build();

        // when then
        assertThrows(RuntimeException.class, () -> chat(model, chatRequest));
    }
}
