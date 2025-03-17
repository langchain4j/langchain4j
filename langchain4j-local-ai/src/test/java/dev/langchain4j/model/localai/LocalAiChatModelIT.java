package dev.langchain4j.model.localai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class LocalAiChatModelIT extends AbstractLocalAiInfrastructure {

    ChatLanguageModel model = LocalAiChatModel.builder()
            .baseUrl(localAi.getBaseUrl())
            .modelName("ggml-gpt4all-j")
            .maxTokens(3)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_send_user_message_and_return_string_response() {

        // given
        String userMessage = "hello";

        // when
        String response = model.generate(userMessage);

        // then
        assertThat(response).isNotBlank();
    }

    @Test
    void should_send_messages_and_return_response() {

        // given
        List<ChatMessage> messages = singletonList(UserMessage.from("hello"));

        // when
        Response<AiMessage> response = model.generate(messages);

        // then
        AiMessage aiMessage = response.content();
        assertThat(aiMessage.text()).isNotBlank();

        assertThat(response.tokenUsage()).isNull();
        assertThat(response.finishReason()).isEqualTo(STOP); // should be LENGTH, this is a bug in LocalAI
    }
}