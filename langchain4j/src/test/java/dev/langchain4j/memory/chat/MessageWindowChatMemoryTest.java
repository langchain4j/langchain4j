package dev.langchain4j.memory.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class MessageWindowChatMemoryTest {

    @Test
    void should_keep_specified_number_of_messages_in_chat_history() {

        SystemMessage systemMessage = systemMessage("bla bla bla");
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .systemMessage(systemMessage)
                .capacity(3)
                .build();
        assertThat(chatMemory.messages())
                .hasSize(1)
                .containsExactly(systemMessage);

        UserMessage firstUserMessage = userMessage("bla bla bla");
        chatMemory.add(firstUserMessage);
        assertThat(chatMemory.messages())
                .hasSize(2)
                .containsExactly(
                        systemMessage,
                        firstUserMessage
                );

        AiMessage firstAiMessage = aiMessage("bla bla bla");
        chatMemory.add(firstAiMessage);
        assertThat(chatMemory.messages())
                .hasSize(3)
                .containsExactly(
                        systemMessage,
                        firstUserMessage,
                        firstAiMessage
                );

        UserMessage secondUserMessage = userMessage("bla bla bla");
        chatMemory.add(secondUserMessage);
        assertThat(chatMemory.messages())
                .hasSize(3)
                .containsExactly(
                        systemMessage,
                        // firstUserMessage was removed
                        firstAiMessage,
                        secondUserMessage
                );

        AiMessage secondAiMessage = aiMessage("bla bla bla");
        chatMemory.add(secondAiMessage);
        assertThat(chatMemory.messages())
                .hasSize(3)
                .containsExactly(
                        systemMessage,
                        // firstAiMessage was removed
                        secondUserMessage,
                        secondAiMessage
                );
    }

    @Test
    void should_keep_specified_number_of_messages_in_chat_history_without_system_message() {

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .capacity(2)
                .build();
        assertThat(chatMemory.messages())
                .hasSize(0);

        UserMessage firstUserMessage = userMessage("bla bla bla");
        chatMemory.add(firstUserMessage);
        assertThat(chatMemory.messages())
                .hasSize(1)
                .containsExactly(firstUserMessage);

        AiMessage firstAiMessage = aiMessage("bla bla bla");
        chatMemory.add(firstAiMessage);
        assertThat(chatMemory.messages())
                .hasSize(2)
                .containsExactly(
                        firstUserMessage,
                        firstAiMessage
                );

        UserMessage secondUserMessage = userMessage("bla bla bla");
        chatMemory.add(secondUserMessage);
        assertThat(chatMemory.messages())
                .hasSize(2)
                .containsExactly(
                        // firstUserMessage was removed
                        firstAiMessage,
                        secondUserMessage
                );

        AiMessage secondAiMessage = aiMessage("bla bla bla");
        chatMemory.add(secondAiMessage);
        assertThat(chatMemory.messages())
                .hasSize(2)
                .containsExactly(
                        // firstAiMessage was removed
                        secondUserMessage,
                        secondAiMessage
                );
    }

    @Test
    void should_load_messages_with_message_restriction() {

        List<ChatMessage> previousMessages = asList(
                userMessage("first"),
                aiMessage("second"),
                userMessage("third"),
                aiMessage("fourth")
        );

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .messages(previousMessages)
                .capacity(3)
                .build();

        assertThat(chatMemory.messages()).containsExactly(
                aiMessage("second"),
                userMessage("third"),
                aiMessage("fourth")
        );
    }
}