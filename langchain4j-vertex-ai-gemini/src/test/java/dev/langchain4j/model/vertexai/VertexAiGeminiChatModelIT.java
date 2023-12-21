package dev.langchain4j.model.vertexai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("To run this test, you must have provide your own endpoint, project and location")
class VertexAiGeminiChatModelIT {

    @Test
    void should_generate_response() {

        // given
        ChatLanguageModel model = VertexAiGeminiChatModel.builder()
                .project("langchain4j")
                .location("us-central1")
                .modelName("gemini-pro")
                .build();

        UserMessage userMessage = UserMessage.from("What is the capital of Germany?");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).contains("Berlin");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(7);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_respect_maxOutputTokens() {

        // given
        ChatLanguageModel model = VertexAiGeminiChatModel.builder()
                .project("langchain4j")
                .location("us-central1")
                .modelName("gemini-pro")
                .maxOutputTokens(1)
                .build();

        UserMessage userMessage = UserMessage.from("Tell me a joke");

        // when
        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        // then
        assertThat(response.content().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(4);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(1);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(FinishReason.LENGTH);
    }
}