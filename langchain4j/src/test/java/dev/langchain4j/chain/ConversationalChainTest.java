package dev.langchain4j.chain;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ConversationalChainTest {

    private static final String QUESTION = "question";
    private static final String ANSWER = "answer";

    @Mock
    ChatLanguageModel chatLanguageModel;

    @Spy
    ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

    @Test
    void should_store_user_and_ai_messages_in_chat_memory() {

        // given
        when(chatLanguageModel.generate(anyList())).thenReturn(Response.from(aiMessage(ANSWER)));

        ConversationalChain chain = ConversationalChain.builder()
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        // when
        String response = chain.execute(QUESTION);

        // then
        assertThat(response).isEqualTo(ANSWER);

        verify(chatMemory).add(userMessage(QUESTION));
        verify(chatMemory, times(3)).messages();
        verify(chatLanguageModel).generate(singletonList(userMessage(QUESTION)));
        verify(chatMemory).add(aiMessage(ANSWER));

        verifyNoMoreInteractions(chatMemory);
        verifyNoMoreInteractions(chatLanguageModel);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void should_fail_when_user_message_is_null_or_blank(String userMessage) {

        // given
        ConversationalChain chain = ConversationalChain.builder()
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThatThrownBy(() -> chain.execute(userMessage))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("userMessage cannot be null or blank");
    }
}
