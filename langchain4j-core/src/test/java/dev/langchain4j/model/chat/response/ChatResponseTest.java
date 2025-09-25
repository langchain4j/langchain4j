package dev.langchain4j.model.chat.response;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

class ChatResponseTest {

    @Test
    void should_keep_backward_compatibility() {

        // given
        AiMessage aiMessage = AiMessage.from("hi");
        TokenUsage tokenUsage = new TokenUsage(1, 2, 3);
        FinishReason finishReason = STOP;

        // when
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(aiMessage)
                .tokenUsage(tokenUsage)
                .finishReason(finishReason)
                .build();

        // then
        assertThat(chatResponse.aiMessage()).isEqualTo(aiMessage);
        assertThat(chatResponse.tokenUsage()).isEqualTo(tokenUsage);
        assertThat(chatResponse.finishReason()).isEqualTo(finishReason);
    }

    @Test
    void should_set_ai_message_and_response_metadata() {

        // given
        AiMessage aiMessage = AiMessage.from("hi");
        TokenUsage tokenUsage = new TokenUsage(1, 2, 3);
        FinishReason finishReason = STOP;
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .tokenUsage(tokenUsage)
                .finishReason(finishReason)
                .build();

        // when
        ChatResponse chatResponse =
                ChatResponse.builder().aiMessage(aiMessage).metadata(metadata).build();

        // then
        assertThat(chatResponse.aiMessage()).isEqualTo(aiMessage);
        assertThat(chatResponse.metadata()).isEqualTo(metadata);

        assertThat(chatResponse.tokenUsage()).isEqualTo(tokenUsage);
        assertThat(chatResponse.finishReason()).isEqualTo(finishReason);
    }

    @Test
    void should_fail_when_both_response_metadata_and_token_usage_are_set() {

        assertThatThrownBy(() -> ChatResponse.builder()
                        .aiMessage(AiMessage.from("hi"))
                        .metadata(ChatResponseMetadata.builder().build())
                        .tokenUsage(new TokenUsage(1, 2, 3))
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot set both 'metadata' and 'tokenUsage' on ChatResponse");
    }

    @Test
    void should_fail_when_both_response_metadata_and_finish_reason_are_set() {

        assertThatThrownBy(() -> ChatResponse.builder()
                        .aiMessage(AiMessage.from("hi"))
                        .metadata(ChatResponseMetadata.builder().build())
                        .finishReason(STOP)
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot set both 'metadata' and 'finishReason' on ChatResponse");
    }

    @Test
    void should_fail_when_no_messages_provided() {
        // when/then
        assertThatThrownBy(() -> ChatRequest.builder().build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");
    }

    @Test
    void should_fail_when_empty_messages_array_provided() {
        // when/then
        assertThatThrownBy(() -> ChatRequest.builder().messages().build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("messages cannot be null or empty");
    }

    @Test
    void should_fail_when_parameters_contains_conflicting_tool_specifications() {
        // given
        ToolSpecification paramTool =
                ToolSpecification.builder().name("paramTool").build();
        ToolSpecification builderTool =
                ToolSpecification.builder().name("builderTool").build();

        ChatRequestParameters parameters =
                ChatRequestParameters.builder().toolSpecifications(paramTool).build();

        // when/then
        assertThatThrownBy(() -> ChatRequest.builder()
                        .messages(UserMessage.from("hi"))
                        .parameters(parameters)
                        .toolSpecifications(builderTool)
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot set both 'parameters' and 'toolSpecifications' on ChatRequest");
    }

    @Test
    void should_handle_null_ai_message() {
        // when/then
        assertThatThrownBy(() -> ChatResponse.builder().aiMessage(null).build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("aiMessage cannot be null");
    }
}
