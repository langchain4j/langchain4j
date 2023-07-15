package dev.langchain4j.chain;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Result;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ConversationalChainTest {

    @Test
    public void testExecute_PositiveCase() {
        // Given
        ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
        ChatMemory chatMemory = MessageWindowChatMemory.withCapacity(5);
        UserMessage userMessage = UserMessage.from("hello Ai.");
        chatMemory.add(userMessage);
        AiMessage aiMessage = AiMessage.from("hello human.");
        chatMemory.add(aiMessage);
        AiMessage result = new AiMessage("Result");
        Result<AiMessage> aiMessageResult = new Result<>(result);
        String userMessage2 = "how are you doing?";
        when(chatLanguageModel.sendMessages(any(List.class))).thenReturn(aiMessageResult);

        ConversationalChain chain = ConversationalChain.builder()
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        // When

        String response = chain.execute(userMessage2);

        // Then
        assertThat(response).isEqualTo("Result");
    }

    @Test
    public void testExecute_NegativeCase_EmptyInput() {
        // Given
        ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
        ChatMemory chatMemory = mock(ChatMemory.class);

        ConversationalChain chain = ConversationalChain.builder()
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        // When & Then
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> chain.execute(""));
    }

    @Test
    public void testExecute_EdgeCase_WhitespaceInput() {
        // Given
        ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
        ChatMemory chatMemory = mock(ChatMemory.class);

        ConversationalChain chain = ConversationalChain.builder()
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        // When & Then
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> chain.execute("   "));
    }

    @Test
    public void testExecute_CornerCase_LongStringInput() {
        // Given
        ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
        ChatMemory chatMemory = mock(ChatMemory.class);
        String userMessage = String.join("", Collections.nCopies(100, "Hello AI"));
        Result<AiMessage> outputAiMessage = new Result<>(new AiMessage("Result"));
        when(chatLanguageModel.sendMessages(any(List.class))).thenReturn(outputAiMessage);

        ConversationalChain chain = ConversationalChain.builder()
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        // When
        String response = chain.execute(userMessage);

        // Then
        assertThat(response).isEqualTo("Result");
        verify(chatMemory, times(2)).add(any());
    }

    @Test
    public void testExecute_NegativeCase_NullResponse() {
        // Given
        ChatLanguageModel chatLanguageModel = mock(ChatLanguageModel.class);
        ChatMemory chatMemory = mock(ChatMemory.class);
        String userMessage = "Hello AI";
        when(chatLanguageModel.sendMessages(any(List.class))).thenReturn(null);

        ConversationalChain chain = ConversationalChain.builder()
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        // When & Then
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> chain.execute(userMessage));
    }

}
