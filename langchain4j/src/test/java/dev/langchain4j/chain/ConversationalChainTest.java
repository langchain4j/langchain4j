package dev.langchain4j.chain;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

public class ConversationalChainTest {

    @Test
    public void should_store_user_and_ai_messages_in_chat_memory() {
        // Given
        ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
        String aiMessage = "Hi there";
        when(chatLanguageModel.generate(anyList())).thenReturn(Response.from(aiMessage(aiMessage)));

        ChatMemory chatMemory = spy(MessageWindowChatMemory.withMaxMessages(10));

        ConversationalChain chain = ConversationalChain.builder()
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        String userMessage = "Hello";

        // When
        String response = chain.execute(userMessage);

        // Then
        assertThat(response).isEqualTo(aiMessage);

        verify(chatMemory).add(userMessage(userMessage));
        verify(chatMemory, times(3)).messages();
        verify(chatLanguageModel).generate(singletonList(userMessage(userMessage)));
        verify(chatMemory).add(aiMessage(aiMessage));

        verifyNoMoreInteractions(chatMemory);
        verifyNoMoreInteractions(chatLanguageModel);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    public void should_fail_when_user_message_is_null_or_blank(String userMessage) {
        // Given
        ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
        ChatMemory chatMemory = mock(ChatMemory.class);

        ConversationalChain chain = ConversationalChain.builder()
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        // When & Then
        assertThatThrownBy(() -> chain.execute(userMessage))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("userMessage cannot be null or blank");
    }

}
