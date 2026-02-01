package dev.langchain4j.chain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ConversationalChainTest {

    private ChatMemory chatMemory;
    private ChatModelMock chatModel;

    @BeforeEach
    void setUp() {
        chatMemory = spy(MessageWindowChatMemory.withMaxMessages(10));
        chatModel = ChatModelMock.thatAlwaysResponds("AI response");
    }

    @Test
    void should_store_user_and_ai_messages_in_chat_memory() {

        // given
        ChatMemory chatMemory = spy(MessageWindowChatMemory.withMaxMessages(10));

        String aiMessage = "Hi there";
        ChatModelMock model = ChatModelMock.thatAlwaysResponds(aiMessage);

        ConversationalChain chain = ConversationalChain.builder()
                .chatModel(model)
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
        ConversationalChain chain =
                ConversationalChain.builder().chatModel(mock(ChatModel.class)).build();

        // when-then
        assertThatThrownBy(() -> chain.execute(userMessage))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("userMessage cannot be null or blank");
    }

    @Test
    void should_build_chain_without_chat_memory() {
        ConversationalChain chain =
                ConversationalChain.builder().chatModel(chatModel).build();

        String response = chain.execute("Hello");

        assertThat(response).isEqualTo("AI response");
    }

    @Test
    void should_fail_when_chat_model_is_null() {
        assertThatThrownBy(() ->
                        ConversationalChain.builder().chatMemory(chatMemory).build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("chatModel cannot be null");
    }

    @Test
    void should_handle_multiple_consecutive_messages() {
        ConversationalChain chain = ConversationalChain.builder()
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .build();

        String response1 = chain.execute("First message");
        String response2 = chain.execute("Second message");
        String response3 = chain.execute("Third message");

        assertThat(response1).isEqualTo("AI response");
        assertThat(response2).isEqualTo("AI response");
        assertThat(response3).isEqualTo("AI response");

        verify(chatMemory, times(3)).add(any(UserMessage.class));
        verify(chatMemory, times(3)).add(any(AiMessage.class));
    }

    @Test
    void should_preserve_conversation_history() {
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(10);
        ConversationalChain chain = ConversationalChain.builder()
                .chatModel(chatModel)
                .chatMemory(memory)
                .build();

        chain.execute("Hello");
        chain.execute("How are you?");

        assertThat(memory.messages()).hasSize(4);
        assertThat(memory.messages().get(0)).isInstanceOf(UserMessage.class);
        assertThat(memory.messages().get(1)).isInstanceOf(AiMessage.class);
        assertThat(memory.messages().get(2)).isInstanceOf(UserMessage.class);
        assertThat(memory.messages().get(3)).isInstanceOf(AiMessage.class);
    }

    @Test
    void should_respect_memory_window_size() {
        ChatMemory limitedMemory = MessageWindowChatMemory.withMaxMessages(2);
        ConversationalChain chain = ConversationalChain.builder()
                .chatModel(chatModel)
                .chatMemory(limitedMemory)
                .build();

        chain.execute("Message 1");
        chain.execute("Message 2");
        chain.execute("Message 3");

        assertThat(limitedMemory.messages()).hasSize(2);
    }

    @Test
    void should_handle_special_characters_in_message() {
        String specialMessage = "Hello! @#$%^&*()_+-={}[]|\\:\";<>?,./~`";
        ConversationalChain chain = ConversationalChain.builder()
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .build();

        String response = chain.execute(specialMessage);

        assertThat(response).isEqualTo("AI response");
        verify(chatMemory).add(UserMessage.from(specialMessage));
    }

    @ParameterizedTest
    @MethodSource("provideWhitespaceVariations")
    void should_handle_messages_with_whitespace_variations(String message, boolean shouldSucceed) {
        ConversationalChain chain =
                ConversationalChain.builder().chatModel(chatModel).build();

        if (shouldSucceed) {
            String response = chain.execute(message);
            assertThat(response).isEqualTo("AI response");
        } else {
            assertThatThrownBy(() -> chain.execute(message))
                    .isExactlyInstanceOf(IllegalArgumentException.class)
                    .hasMessage("userMessage cannot be null or blank");
        }
    }

    private static Stream<Arguments> provideWhitespaceVariations() {
        return Stream.of(
                Arguments.of("  Hello  ", true), // trimmed message
                Arguments.of("Hello\nWorld", true), // with newline
                Arguments.of("Hello\tWorld", true), // with tab
                Arguments.of("\n\n", false), // only newlines
                Arguments.of("\t\t", false), // only tabs
                Arguments.of("   ", false) // only spaces
                );
    }

    @Test
    void should_work_with_custom_chat_memory_implementation() {
        ChatMemory customMemory = mock(ChatMemory.class);
        when(customMemory.messages())
                .thenReturn(List.of(UserMessage.from("Previous message"), AiMessage.from("Previous response")));

        ConversationalChain chain = ConversationalChain.builder()
                .chatModel(chatModel)
                .chatMemory(customMemory)
                .build();

        String response = chain.execute("Hello");

        assertThat(response).isEqualTo("AI response");
        verify(customMemory).add(any(UserMessage.class));
        verify(customMemory).add(any(AiMessage.class));
        verify(customMemory, atLeastOnce()).messages();
    }

    @Test
    void should_handle_system_message_in_memory() {
        ChatMemory memoryWithSystem = MessageWindowChatMemory.withMaxMessages(10);
        memoryWithSystem.add(SystemMessage.from("You are a helpful assistant"));

        ConversationalChain chain = ConversationalChain.builder()
                .chatModel(chatModel)
                .chatMemory(memoryWithSystem)
                .build();

        String response = chain.execute("Hello");

        assertThat(response).isEqualTo("AI response");
        assertThat(memoryWithSystem.messages()).hasSize(3); // system + user + ai
        assertThat(memoryWithSystem.messages().get(0)).isInstanceOf(SystemMessage.class);
    }
}
