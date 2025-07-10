package dev.langchain4j.model.vertexai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("TODO: configure custom model")
class VertexAiChatModelIT {

    @Test
    void chatModel() {

        VertexAiChatModel vertexAiChatModel = VertexAiChatModel.builder()
                .endpoint(System.getenv("GCP_VERTEXAI_ENDPOINT"))
                .project(System.getenv("GCP_PROJECT_ID"))
                .location(System.getenv("GCP_LOCATION"))
                .publisher("google")
                .modelName("chat-bison@001")
                .temperature(1.0)
                .maxOutputTokens(50)
                .topK(0)
                .topP(0.0)
                .maxRetries(2)
                .build();

        ChatResponse response = vertexAiChatModel.chat(UserMessage.from("hi, how are you doing?"));

        assertThat(response.aiMessage().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(7);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(1);
        assertThat(tokenUsage.totalTokenCount()).isGreaterThan(8);

        assertThat(response.finishReason()).isNull();
    }
}
