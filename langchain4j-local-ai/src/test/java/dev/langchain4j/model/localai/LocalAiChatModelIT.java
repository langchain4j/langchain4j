package dev.langchain4j.model.localai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class LocalAiChatModelIT {

    ChatModel model = LocalAiChatModel.builder()
            .baseUrl("http://localhost:8082/v1")
            .modelName("gpt-4")
            .maxTokens(3)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_send_user_message_and_return_string_response() {

        // given
        String userMessage = "hello";

        // when
        String response = model.chat(userMessage);

        // then
        assertThat(response).isNotBlank();
    }

    @Test
    void should_send_messages_and_return_response() {

        // given
        List<ChatMessage> messages = singletonList(UserMessage.from("hello"));

        // when
        ChatResponse response = model.chat(messages);

        // then
        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isNotBlank();

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isEqualTo(STOP); // should be LENGTH, this is a bug in LocalAI
    }
}
