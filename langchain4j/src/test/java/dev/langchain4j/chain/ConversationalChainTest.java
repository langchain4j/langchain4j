package dev.langchain4j.chain;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ConversationalChainTest {

    @Test
    void should_store_user_and_ai_messages_in_chat_memory() {

        // given
        ChatMemory chatMemory = spy(MessageWindowChatMemory.withMaxMessages(10));

        String aiMessage = "Hi there";
        ChatModelMock model = ChatModelMock.withStaticResponse(aiMessage);

        ConversationalChain chain = ConversationalChain.builder()
                .chatLanguageModel(model)
                .chatMemory(chatMemory)
                .build();

        String userMessage = "Hello";

        // When
        String response = chain.execute(userMessage);

        // then
        assertThat(response).isEqualTo(aiMessage);

        verify(chatMemory).add(UserMessage.from(userMessage));
        verify(chatMemory, times(3)).messages();
        verify(chatMemory).add(AiMessage.from(aiMessage));
        verifyNoMoreInteractions(chatMemory);

        assertThat(model.userMessageText()).isEqualTo(userMessage);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void should_fail_when_user_message_is_null_or_blank(String userMessage) {

        // given
        ConversationalChain chain = ConversationalChain.builder()
                .chatLanguageModel(mock(ChatLanguageModel.class))
                .build();

        // when-then
        assertThatThrownBy(() -> chain.execute(userMessage))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("userMessage cannot be null or blank");
    }
}
