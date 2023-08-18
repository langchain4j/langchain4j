package dev.langchain4j.memory.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.internal.TestUtils.aiMessageWithTokens;
import static dev.langchain4j.internal.TestUtils.userMessageWithTokens;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static org.assertj.core.api.Assertions.assertThat;

class TokenWindowChatMemoryTest {

    @Test
    void should_keep_specified_number_of_tokens_in_chat_memory() {

        OpenAiTokenizer tokenizer = new OpenAiTokenizer(GPT_3_5_TURBO);
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(33, tokenizer);
        assertThat(chatMemory.messages()).isEmpty();

        UserMessage firstUserMessage = userMessageWithTokens(10);
        chatMemory.add(firstUserMessage);
        assertThat(chatMemory.messages())
                .hasSize(1)
                .containsExactly(firstUserMessage);
        // @formatter:off
        assertThat(tokenizer.estimateTokenCountInMessages(chatMemory.messages())).isEqualTo(
                  10 // firstUserMessage
                + 3  // overhead
        );
        // @formatter:on

        AiMessage firstAiMessage = aiMessageWithTokens(10);
        chatMemory.add(firstAiMessage);
        assertThat(chatMemory.messages())
                .hasSize(2)
                .containsExactly(firstUserMessage, firstAiMessage);
        // @formatter:off
        assertThat(tokenizer.estimateTokenCountInMessages(chatMemory.messages())).isEqualTo(
                  10 // firstUserMessage
                + 10 // firstAiMessage
                + 3  // overhead
        );
        // @formatter:on

        UserMessage secondUserMessage = userMessageWithTokens(10);
        chatMemory.add(secondUserMessage);
        assertThat(chatMemory.messages())
                .hasSize(3)
                .containsExactly(
                        firstUserMessage,
                        firstAiMessage,
                        secondUserMessage
                );
        // @formatter:off
        assertThat(tokenizer.estimateTokenCountInMessages(chatMemory.messages())).isEqualTo(
                 10 // firstUserMessage
               + 10 // firstAiMessage
               + 10 // secondUserMessage
               + 3  // overhead
        );
        // @formatter:on

        AiMessage secondAiMessage = aiMessageWithTokens(10);
        chatMemory.add(secondAiMessage);
        assertThat(chatMemory.messages())
                .hasSize(3)
                .containsExactly(
                        // firstUserMessage was removed
                        firstAiMessage,
                        secondUserMessage,
                        secondAiMessage
                );
        // @formatter:off
        assertThat(tokenizer.estimateTokenCountInMessages(chatMemory.messages())).isEqualTo(
                 10 // firstAiMessage
               + 10 // secondUserMessage
               + 10 // secondAiMessage
               + 3  // overhead
        );
        // @formatter:on
    }
}