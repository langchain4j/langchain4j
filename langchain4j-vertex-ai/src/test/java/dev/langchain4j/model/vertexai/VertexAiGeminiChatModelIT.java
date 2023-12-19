package dev.langchain4j.model.vertexai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VertexAiGeminiChatModelIT {

    @Test
    @Disabled("To run this test, you must have provide your own endpoint, project and location")
    void testChatModel() {

        VertexAiGeminiChatModel geminiChatModel = VertexAiGeminiChatModel.builder()
                .project("langchain4j")
                .location("us-central1")
                .modelName("gemini-pro")
                .build();

        Response<AiMessage> response = geminiChatModel.generate(UserMessage.from("What is the capital of Germany?"));

        assertThat(response.content().text()).contains("Berlin");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(7);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThanOrEqualTo(1);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
    }

}