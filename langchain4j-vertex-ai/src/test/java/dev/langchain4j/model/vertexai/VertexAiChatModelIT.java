package dev.langchain4j.model.vertexai;

import dev.langchain4j.data.message.AiMessage;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VertexAiChatModelIT {

    @Test
  //  @Disabled("To run this test, you must have provide your own endpoint, project and location")
    void testChatModel() {
        VertexAiChatModel vertexAiChatModel = new VertexAiChatModel(
                "us-central1-aiplatform.googleapis.com:443",
                "langchain4j",
                "us-central1",
                "google",
                "chat-bison@001",
                1.0,
                50,
                0,
                0.0,
                3);

        AiMessage aiMessage = vertexAiChatModel.sendUserMessage("hi, how are you doing?");

        assertThat(aiMessage).isNotNull();
        System.out.println(aiMessage.text());
    }

}