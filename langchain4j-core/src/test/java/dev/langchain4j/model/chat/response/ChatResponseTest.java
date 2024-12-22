package dev.langchain4j.model.chat.response;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        ChatResponse chatResponse = ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(metadata)
                .build();

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
}
