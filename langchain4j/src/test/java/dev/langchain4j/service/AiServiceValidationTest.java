package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

class AiServiceValidationTest {

    private static final ChatModel CHAT_MODEL = ChatModelMock.thatAlwaysResponds("Hello there!");

    interface AssistantReturningResultOfChatResponse {

        Result<ChatResponse> chat(String input);
    }

    @Test
    void should_reject_result_of_chat_response() {
        assertThatThrownBy(() -> AiServices.builder(AssistantReturningResultOfChatResponse.class)
                        .chatModel(CHAT_MODEL)
                        .build())
                .isInstanceOf(IllegalConfigurationException.class)
                .hasMessage(
                        "The return type 'Result<ChatResponse>' of the method 'chat' is invalid because Result already contains the final ChatResponse");
    }

    interface AssistantReturningResultOfAiMessage {

        Result<AiMessage> chat(String input);
    }

    @Test
    void should_allow_result_of_ai_message() {
        assertThatCode(() -> AiServices.builder(AssistantReturningResultOfAiMessage.class)
                        .chatModel(CHAT_MODEL)
                        .build())
                .doesNotThrowAnyException();
    }
}
