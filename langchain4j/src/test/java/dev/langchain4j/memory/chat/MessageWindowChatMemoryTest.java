package dev.langchain4j.memory.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

class MessageWindowChatMemoryTest {

    @Test
    void should_keep_specified_number_of_messages_in_chat_memory() {

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(3);

        UserMessage firstUserMessage = userMessage("hello");
        chatMemory.add(firstUserMessage);
        assertThat(chatMemory.messages())
                .hasSize(1)
                .containsExactly(firstUserMessage);

        AiMessage firstAiMessage = aiMessage("hi");
        chatMemory.add(firstAiMessage);
        assertThat(chatMemory.messages())
                .hasSize(2)
                .containsExactly(firstUserMessage, firstAiMessage);

        UserMessage secondUserMessage = userMessage("sup");
        chatMemory.add(secondUserMessage);
        assertThat(chatMemory.messages())
                .hasSize(3)
                .containsExactly(
                        firstUserMessage,
                        firstAiMessage,
                        secondUserMessage
                );

        AiMessage secondAiMessage = aiMessage("not much");
        chatMemory.add(secondAiMessage);
        assertThat(chatMemory.messages())
                .hasSize(3)
                .containsExactly(
                        // firstUserMessage was removed
                        firstAiMessage,
                        secondUserMessage,
                        secondAiMessage
                );
    }

    @Test
    void should_not_remove_system_message_from_chat_memory() {

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(3);

        SystemMessage systemMessage = systemMessage("You are a helpful assistant");
        chatMemory.add(systemMessage);

        UserMessage firstUserMessage = userMessage("Hello");
        chatMemory.add(firstUserMessage);

        AiMessage firstAiMessage = aiMessage("Hi, how can I help you?");
        chatMemory.add(firstAiMessage);

        assertThat(chatMemory.messages()).containsExactly(
                systemMessage,
                firstUserMessage,
                firstAiMessage
        );

        UserMessage secondUserMessage = userMessage("Tell me a joke");
        chatMemory.add(secondUserMessage);

        assertThat(chatMemory.messages()).containsExactly(
                systemMessage,
                // firstUserMessage was removed
                firstAiMessage,
                secondUserMessage
        );

        AiMessage secondAiMessage = aiMessage("Why did the Java developer wear glasses? Because they didn't see sharp!");
        chatMemory.add(secondAiMessage);
        assertThat(chatMemory.messages()).containsExactly(
                systemMessage,
                // firstAiMessage was removed
                secondUserMessage,
                secondAiMessage
        );
    }

    @Test
    void should_keep_only_the_latest_system_message_in_chat_memory() {

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(3);

        SystemMessage firstSystemMessage = systemMessage("You are a helpful assistant");
        chatMemory.add(firstSystemMessage);

        UserMessage firstUserMessage = userMessage("Hello");
        chatMemory.add(firstUserMessage);

        AiMessage firstAiMessage = aiMessage("Hi, how can I help you?");
        chatMemory.add(firstAiMessage);

        assertThat(chatMemory.messages()).containsExactly(
                firstSystemMessage,
                firstUserMessage,
                firstAiMessage
        );

        SystemMessage secondSystemMessage = systemMessage("You are an unhelpful assistant");
        chatMemory.add(secondSystemMessage);
        assertThat(chatMemory.messages()).containsExactly(
                // firstSystemMessage was removed
                firstUserMessage,
                firstAiMessage,
                secondSystemMessage
        );
    }

    @Test
    void should_not_add_the_same_system_message_to_chat_memory_if_it_is_already_there() {

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(3);

        SystemMessage systemMessage = systemMessage("You are a helpful assistant");
        chatMemory.add(systemMessage);

        UserMessage userMessage = userMessage("Hello");
        chatMemory.add(userMessage);

        AiMessage aiMessage = aiMessage("Hi, how can I help you?");
        chatMemory.add(aiMessage);

        assertThat(chatMemory.messages()).containsExactly(
                systemMessage,
                userMessage,
                aiMessage
        );

        chatMemory.add(systemMessage);

        assertThat(chatMemory.messages()).containsExactly(
                systemMessage,
                userMessage,
                aiMessage
        );
    }
}