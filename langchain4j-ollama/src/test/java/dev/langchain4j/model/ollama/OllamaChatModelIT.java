package dev.langchain4j.model.ollama;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

class OllamaChatModelIT extends AbstractOllamaInfrastructure {

    OllamaChatModel model = OllamaChatModel.builder()
            .baseUrl(getBaseUrl())
            .modelName(ORCA_MINI_MODEL)
            .build();

    @Test
    void should_send_messages_with_roles_and_receive_response() {

        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(systemMessage("You are a good friend of mine, who likes to answer politely"));
        chatMessages.add(userMessage("Hello!, How are you?"));
        chatMessages.add(AiMessage.aiMessage("I'm fine, thanks!"));
        chatMessages.add(userMessage("Not too bad, just enjoying a cup of coffee. What about you?"));

        Response<AiMessage> response = model.generate(chatMessages);
        System.out.println(response);

        assertThat(response).isNotNull();
        assertThat(response.content().text()).isNotEmpty();
    }
}