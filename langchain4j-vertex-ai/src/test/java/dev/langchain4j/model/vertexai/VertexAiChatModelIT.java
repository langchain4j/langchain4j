package dev.langchain4j.model.vertexai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VertexAiChatModelIT {

    @Test
    @Disabled("To run this test, you must have provide your own endpoint, project and location")
    void testChatModel() {

        VertexAiChatModel vertexAiChatModel = VertexAiChatModel.builder()
                .endpoint("us-central1-aiplatform.googleapis.com:443")
                .project("langchain4j")
                .location("us-central1")
                .publisher("google")
                .modelName("chat-bison@001")
                .temperature(1.0)
                .maxOutputTokens(50)
                .topK(0)
                .topP(0.0)
                .maxRetries(3)
                .build();

        Response<AiMessage> response = vertexAiChatModel.generate(UserMessage.from("hi, how are you doing?"));
        System.out.println(response);

        assertThat(response.content().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(7);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(1);
        assertThat(tokenUsage.totalTokenCount()).isGreaterThan(8);

        assertThat(response.finishReason()).isNull();
    }
}