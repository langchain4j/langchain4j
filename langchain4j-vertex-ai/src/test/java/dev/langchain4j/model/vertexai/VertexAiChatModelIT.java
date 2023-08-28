package dev.langchain4j.model.vertexai;

import dev.langchain4j.data.message.AiMessage;
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

        AiMessage aiMessage = vertexAiChatModel.sendUserMessage("hi, how are you doing?");

        assertThat(aiMessage).isNotNull();
        System.out.println(aiMessage.text());
    }

}