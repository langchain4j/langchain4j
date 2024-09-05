package dev.langchain4j.model.vertexai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VertexAiChatModelIT {

    @Test
    void testChatModel() {

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
                .maxRetries(3)
                .build();

        Response<AiMessage> response = vertexAiChatModel.generate(UserMessage.from("hi, how are you doing?"));

        assertThat(response.content().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(7);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(1);
        assertThat(tokenUsage.totalTokenCount()).isGreaterThan(8);

        assertThat(response.finishReason()).isNull();
    }
}