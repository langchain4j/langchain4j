package dev.langchain4j.model.vertex;

import dev.langchain4j.data.message.AiMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VertexAiChatModelTest {

    @Test
    void testChatModel() {
        VertexAiChatModel vertexAiChatModel = new VertexAiChatModel(
                "chat-bison@001",
                "langchain4j",
                "us-central1",
                "google",
                "us-central1-aiplatform.googleapis.com:443",
                1.0,
                50,
                0,
                0.0);

        AiMessage aiMessage = vertexAiChatModel.sendUserMessage("hi, how are you doing?");

        assertThat(aiMessage).isNotNull();
        System.out.println(aiMessage.text());
    }

}