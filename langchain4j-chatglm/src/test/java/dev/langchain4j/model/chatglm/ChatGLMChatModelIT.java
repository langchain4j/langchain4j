package dev.langchain4j.model.chatglm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

class ChatGLMChatModelIT {

    ChatLanguageModel model = ChatGLMChatModel.builder()
            .baseUrl("http://localhost:8000")
            .build();

    @Test
    void should_generate_answer() {

        // given
        UserMessage userMessage = userMessage("What is the capital of Germany?");

        // when
        Response<AiMessage> response = model.generate(userMessage);

        // then
        assertThat(response.content().text()).contains("Berlin");
    }
}
