package dev.langchain4j.memory.chat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.internal.TestUtils.*;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static org.assertj.core.api.Assertions.assertThat;

class TokenWindowChatMemoryTest {

    @Test
    void should_keep_specified_number_of_tokens_in_chat_memory() {

        OpenAiTokenizer tokenizer = new OpenAiTokenizer(GPT_3_5_TURBO);
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(33, tokenizer);

        UserMessage firstUserMessage = userMessageWithTokens(10);
        chatMemory.add(firstUserMessage);
        assertThat(chatMemory.messages()).containsExactly(firstUserMessage);
        // @formatter:off
        assertThat(tokenizer.estimateTokenCountInMessages(chatMemory.messages())).isEqualTo(
                  10 // firstUserMessage
                + 3  // overhead
        );
        // @formatter:on

        AiMessage firstAiMessage = aiMessageWithTokens(10);
        chatMemory.add(firstAiMessage);
        assertThat(chatMemory.messages()).containsExactly(firstUserMessage, firstAiMessage);
        // @formatter:off
        assertThat(tokenizer.estimateTokenCountInMessages(chatMemory.messages())).isEqualTo(
                  10 // firstUserMessage
                + 10 // firstAiMessage
                + 3  // overhead
        );
        // @formatter:on

        UserMessage secondUserMessage = userMessageWithTokens(10);
        chatMemory.add(secondUserMessage);
        assertThat(chatMemory.messages()).containsExactly(
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
        assertThat(chatMemory.messages()).containsExactly(
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

    @Test
    void should_not_remove_system_message_from_chat_memory() {

        OpenAiTokenizer tokenizer = new OpenAiTokenizer(GPT_3_5_TURBO);
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(33, tokenizer);

        SystemMessage systemMessage = systemMessageWithTokens(10);
        chatMemory.add(systemMessage);

        UserMessage firstUserMessage = userMessageWithTokens(10);
        chatMemory.add(firstUserMessage);

        AiMessage firstAiMessage = aiMessageWithTokens(10);
        chatMemory.add(firstAiMessage);

        assertThat(chatMemory.messages()).containsExactly(
                systemMessage,
                firstUserMessage,
                firstAiMessage
        );

        UserMessage secondUserMessage = userMessageWithTokens(10);
        chatMemory.add(secondUserMessage);
        assertThat(chatMemory.messages()).containsExactly(
                systemMessage,
                // firstUserMessage was removed
                firstAiMessage,
                secondUserMessage
        );

        AiMessage secondAiMessage = aiMessageWithTokens(10);
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

        OpenAiTokenizer tokenizer = new OpenAiTokenizer(GPT_3_5_TURBO);
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(40, tokenizer);

        SystemMessage firstSystemMessage = systemMessage("You are a helpful assistant");
        chatMemory.add(firstSystemMessage);

        UserMessage firstUserMessage = userMessageWithTokens(10);
        chatMemory.add(firstUserMessage);

        AiMessage firstAiMessage = aiMessageWithTokens(10);
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

        OpenAiTokenizer tokenizer = new OpenAiTokenizer(GPT_3_5_TURBO);
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(33, tokenizer);

        SystemMessage systemMessage = systemMessageWithTokens(10);
        chatMemory.add(systemMessage);

        UserMessage userMessage = userMessageWithTokens(10);
        chatMemory.add(userMessage);

        AiMessage aiMessage = aiMessageWithTokens(10);
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