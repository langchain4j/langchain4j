package dev.langchain4j.model.ollama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

@Disabled("needs Ollama running locally")
class OllamaChatModelIT {

    OllamaChatModel model = OllamaChatModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("orca-mini")
            .temperature(0.5)
            .build();

    @Test
    void should_send_messages_and_receive_response() {

        // create a list but without using the of method
        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(systemMessage("You are a good friend of mine, who likes to answer with jokes"));
        chatMessages.add(userMessage("Hey Bro, what are you doing?"));

        Response<AiMessage> response = model.generate(chatMessages);
        System.out.println(response);

        assertThat(response).isNotNull();
    }

}