package dev.langchain4j.memory.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

class MessageWindowChatMemoryTest {

    @Test
    void should_keep_specified_number_of_messages_in_chat_memory() {

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(3);
        assertThat(chatMemory.messages()).isEmpty();

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
}