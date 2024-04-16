package dev.langchain4j.memory.chat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.TestUtils.*;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static java.util.Collections.singletonList;

class TokenWindowChatMemoryTest implements WithAssertions {

    private static final Tokenizer TOKENIZER = new OpenAiTokenizer(GPT_3_5_TURBO);
    private static final int EXTRA_TOKENS_PER_REQUEST = 3;

    @Test
    void test_id() {
        {
            ChatMemory chatMemory = TokenWindowChatMemory.builder()
                    .maxTokens(2, TOKENIZER)
                    .build();
            assertThat(chatMemory.id()).isEqualTo("default");
        }
        {
            ChatMemory chatMemory = TokenWindowChatMemory.builder()
                    .id("abc")
                    .maxTokens(2, TOKENIZER)
                    .build();
            assertThat(chatMemory.id()).isEqualTo("abc");
        }
    }

    @Test
    void test_store_and_clear() {

        ChatMessage m1 = userMessage("hello");
        ChatMessage m2 = userMessage("world");
        ChatMessage m3 = userMessage("banana");

        int m1tokens = TOKENIZER.estimateTokenCountInMessage(m1);
        int m2tokens = TOKENIZER.estimateTokenCountInMessage(m2);

        assertThat(TOKENIZER.estimateTokenCountInMessages(singletonList(m1)))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + m1tokens);

        ChatMemory chatMemory = TokenWindowChatMemory.builder()
                .maxTokens(EXTRA_TOKENS_PER_REQUEST + m1tokens + m2tokens, TOKENIZER)
                .build();

        chatMemory.add(m1);
        chatMemory.add(m2);

        assertThat(chatMemory.messages())
                .containsExactly(m1, m2);

        chatMemory.add(m3);

        assertThat(chatMemory.messages())
                .containsExactly(m2, m3);

        chatMemory.clear();
        // idempotent
        chatMemory.clear();

        assertThat(chatMemory.messages()).isEmpty();
    }

    @Test
    void should_keep_specified_number_of_tokens_in_chat_memory() {

        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(33, TOKENIZER);

        UserMessage firstUserMessage = userMessageWithTokens(10);
        chatMemory.add(firstUserMessage);
        assertThat(chatMemory.messages()).containsExactly(firstUserMessage);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages())).isEqualTo(
                EXTRA_TOKENS_PER_REQUEST
                        + 10 // firstUserMessage
        );

        AiMessage firstAiMessage = aiMessageWithTokens(10);
        chatMemory.add(firstAiMessage);
        assertThat(chatMemory.messages()).containsExactly(firstUserMessage, firstAiMessage);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages())).isEqualTo(
                EXTRA_TOKENS_PER_REQUEST
                        + 10 // firstUserMessage
                        + 10 // firstAiMessage
        );

        UserMessage secondUserMessage = userMessageWithTokens(10);
        chatMemory.add(secondUserMessage);
        assertThat(chatMemory.messages()).containsExactly(
                firstUserMessage,
                firstAiMessage,
                secondUserMessage
        );
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages())).isEqualTo(
                EXTRA_TOKENS_PER_REQUEST
                        + 10 // firstUserMessage
                        + 10 // firstAiMessage
                        + 10 // secondUserMessage
        );

        AiMessage secondAiMessage = aiMessageWithTokens(10);
        chatMemory.add(secondAiMessage);
        assertThat(chatMemory.messages()).containsExactly(
                // firstUserMessage was evicted
                firstAiMessage,
                secondUserMessage,
                secondAiMessage
        );
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages())).isEqualTo(
                EXTRA_TOKENS_PER_REQUEST
                        + 10 // firstAiMessage
                        + 10 // secondUserMessage
                        + 10 // secondAiMessage
        );
    }

    @Test
    void should_not_evict_system_message_from_chat_memory() {

        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(33, TOKENIZER);

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
                // firstUserMessage was evicted
                firstAiMessage,
                secondUserMessage
        );

        AiMessage secondAiMessage = aiMessageWithTokens(10);
        chatMemory.add(secondAiMessage);
        assertThat(chatMemory.messages()).containsExactly(
                systemMessage,
                // firstAiMessage was evicted
                secondUserMessage,
                secondAiMessage
        );
    }

    @Test
    void should_keep_only_the_latest_system_message_in_chat_memory() {

        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(40, TOKENIZER);

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
                // firstSystemMessage was evicted
                firstUserMessage,
                firstAiMessage,
                secondSystemMessage
        );
    }

    @Test
    void should_not_add_the_same_system_message_to_chat_memory_if_it_is_already_there() {

        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(33, TOKENIZER);

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

    @Test
    void should_evict_orphan_ToolExecutionResultMessage_when_evicting_AiMessage_with_ToolExecutionRequest() {

        // given
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(36, TOKENIZER);


        // when
        UserMessage userMessage = UserMessage.from("How much is 2+2?");
        int userMessageTokens = TOKENIZER.estimateTokenCountInMessage(userMessage);
        assertThat(userMessageTokens).isEqualTo(12);
        chatMemory.add(userMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(userMessage);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + userMessageTokens)
                .isEqualTo(15);


        // when
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("calculator")
                .arguments("{ \"a\": 2, \"b\": 2 }")
                .build();
        AiMessage aiMessage = AiMessage.from(toolExecutionRequest);
        int aiMessageTokens = TOKENIZER.estimateTokenCountInMessage(aiMessage);
        assertThat(aiMessageTokens).isEqualTo(21);
        chatMemory.add(aiMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(userMessage, aiMessage);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + userMessageTokens + aiMessageTokens)
                .isEqualTo(36);


        // when
        ToolExecutionResultMessage toolExecutionResultMessage =
                ToolExecutionResultMessage.from(toolExecutionRequest, "4");
        int toolExecutionResultMessageTokens = TOKENIZER.estimateTokenCountInMessage(toolExecutionResultMessage);
        assertThat(toolExecutionResultMessageTokens).isEqualTo(5);
        chatMemory.add(toolExecutionResultMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(aiMessage, toolExecutionResultMessage);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + aiMessageTokens + toolExecutionResultMessageTokens)
                .isEqualTo(29);


        // when new message is added and aiMessage (containing ToolExecutionRequest) has to be evicted
        AiMessage aiMessage2 = AiMessage.from("2 + 2 = 4");
        int aiMessage2Tokens = TOKENIZER.estimateTokenCountInMessage(aiMessage2);
        assertThat(aiMessage2Tokens).isEqualTo(11);
        chatMemory.add(aiMessage2);

        // then orphan toolExecutionResultMessage is evicted together with aiMessage
        assertThat(chatMemory.messages()).containsExactly(aiMessage2);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + aiMessage2Tokens)
                .isEqualTo(14);
    }

    @Test
    void should_evict_orphan_ToolExecutionResultMessage_when_evicting_AiMessage_with_ToolExecutionRequest_when_SystemMessage_is_present() {

        // given
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(45, TOKENIZER);


        // when
        SystemMessage systemMessage = SystemMessage.from("Use calculator for math questions");
        int systemMessageTokens = TOKENIZER.estimateTokenCountInMessage(systemMessage);
        assertThat(systemMessageTokens).isEqualTo(9);
        chatMemory.add(systemMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens)
                .isEqualTo(12);

        // when
        UserMessage userMessage = UserMessage.from("How much is 2+2?");
        int userMessageTokens = TOKENIZER.estimateTokenCountInMessage(userMessage);
        assertThat(userMessageTokens).isEqualTo(12);
        chatMemory.add(userMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens + userMessageTokens)
                .isEqualTo(24);


        // when
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("calculator")
                .arguments("{ \"a\": 2, \"b\": 2 }")
                .build();
        AiMessage aiMessage = AiMessage.from(toolExecutionRequest);
        int aiMessageTokens = TOKENIZER.estimateTokenCountInMessage(aiMessage);
        assertThat(aiMessageTokens).isEqualTo(21);
        chatMemory.add(aiMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage, aiMessage);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens + userMessageTokens + aiMessageTokens)
                .isEqualTo(45);


        // when
        ToolExecutionResultMessage toolExecutionResultMessage =
                ToolExecutionResultMessage.from(toolExecutionRequest, "4");
        int toolExecutionResultMessageTokens = TOKENIZER.estimateTokenCountInMessage(toolExecutionResultMessage);
        assertThat(toolExecutionResultMessageTokens).isEqualTo(5);
        chatMemory.add(toolExecutionResultMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, aiMessage, toolExecutionResultMessage);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens + aiMessageTokens + toolExecutionResultMessageTokens)
                .isEqualTo(38);

        // when aiMessage2 is added and aiMessage has to be evicted
        AiMessage aiMessage2 = AiMessage.from("2 + 2 = 4");
        int aiMessage2Tokens = TOKENIZER.estimateTokenCountInMessage(aiMessage2);
        assertThat(aiMessage2Tokens).isEqualTo(11);
        chatMemory.add(aiMessage2);

        // then orphan toolExecutionResultMessage is evicted together with aiMessage
        assertThat(chatMemory.messages()).containsExactly(systemMessage, aiMessage2);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens + aiMessage2Tokens)
                .isEqualTo(23);
    }

    @Test
    void should_evict_orphan_ToolExecutionResultMessage_when_evicting_AiMessage_with_ToolExecutionRequest_when_SystemMessage_is_present_2() {

        // given chat memory
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(33, TOKENIZER);


        // when
        SystemMessage systemMessage = SystemMessage.from("Use calculator for math questions");
        int systemMessageTokens = TOKENIZER.estimateTokenCountInMessage(systemMessage);
        assertThat(systemMessageTokens).isEqualTo(9);
        chatMemory.add(systemMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens)
                .isEqualTo(12);


        // when
        UserMessage userMessage = UserMessage.from("How much is 2+2?");
        int userMessageTokens = TOKENIZER.estimateTokenCountInMessage(userMessage);
        assertThat(userMessageTokens).isEqualTo(12);
        chatMemory.add(userMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens + userMessageTokens)
                .isEqualTo(24);


        // when
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("calculator")
                .arguments("{ \"a\": 2, \"b\": 2 }")
                .build();
        AiMessage aiMessage = AiMessage.from(toolExecutionRequest);
        int aiMessageTokens = TOKENIZER.estimateTokenCountInMessage(aiMessage);
        assertThat(aiMessageTokens).isEqualTo(21);
        chatMemory.add(aiMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, aiMessage);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens + aiMessageTokens)
                .isEqualTo(33);


        // when toolExecutionResultMessage is added and aiMessage has to be evicted
        ToolExecutionResultMessage toolExecutionResultMessage =
                ToolExecutionResultMessage.from(toolExecutionRequest, "4");
        int toolExecutionResultMessageTokens = TOKENIZER.estimateTokenCountInMessage(toolExecutionResultMessage);
        assertThat(toolExecutionResultMessageTokens).isEqualTo(5);
        chatMemory.add(toolExecutionResultMessage);

        // then orphan toolExecutionResultMessage is evicted together with aiMessage
        assertThat(chatMemory.messages()).containsExactly(systemMessage);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens)
                .isEqualTo(12);
    }

    @Test
    void should_evict_multiple_orphan_ToolExecutionResultMessages_when_evicting_AiMessage_with_ToolExecutionRequests() {

        // given
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(87, TOKENIZER);


        // when
        UserMessage userMessage = UserMessage.from("How much is 2+2 and 3+3?");
        int userMessageTokens = TOKENIZER.estimateTokenCountInMessage(userMessage);
        assertThat(userMessageTokens).isEqualTo(17);
        chatMemory.add(userMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(userMessage);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + userMessageTokens)
                .isEqualTo(20);


        // when
        ToolExecutionRequest toolExecutionRequest1 = ToolExecutionRequest.builder()
                .id("1")
                .name("calculator")
                .arguments("{ \"a\": 2, \"b\": 2 }")
                .build();
        ToolExecutionRequest toolExecutionRequest2 = ToolExecutionRequest.builder()
                .id("2")
                .name("calculator")
                .arguments("{ \"a\": 3, \"b\": 3 }")
                .build();
        AiMessage aiMessage = AiMessage.from(toolExecutionRequest1, toolExecutionRequest2);
        int aiMessageTokens = TOKENIZER.estimateTokenCountInMessage(aiMessage);
        assertThat(aiMessageTokens).isEqualTo(62);
        chatMemory.add(aiMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(userMessage, aiMessage);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + userMessageTokens + aiMessageTokens)
                .isEqualTo(82);


        // when
        ToolExecutionResultMessage toolExecutionResultMessage1 =
                ToolExecutionResultMessage.from(toolExecutionRequest1, "4");
        int toolExecutionResultMessage1Tokens = TOKENIZER.estimateTokenCountInMessage(toolExecutionResultMessage1);
        assertThat(toolExecutionResultMessage1Tokens).isEqualTo(5);
        chatMemory.add(toolExecutionResultMessage1);

        // then
        assertThat(chatMemory.messages()).containsExactly(userMessage, aiMessage, toolExecutionResultMessage1);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + userMessageTokens + aiMessageTokens + toolExecutionResultMessage1Tokens)
                .isEqualTo(87);

        // when
        ToolExecutionResultMessage toolExecutionResultMessage2 =
                ToolExecutionResultMessage.from(toolExecutionRequest2, "6");
        int toolExecutionResultMessage2Tokens = TOKENIZER.estimateTokenCountInMessage(toolExecutionResultMessage2);
        assertThat(toolExecutionResultMessage2Tokens).isEqualTo(5);
        chatMemory.add(toolExecutionResultMessage2);

        // then
        assertThat(chatMemory.messages())
                .containsExactly(aiMessage, toolExecutionResultMessage1, toolExecutionResultMessage2);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + aiMessageTokens + toolExecutionResultMessage1Tokens + toolExecutionResultMessage2Tokens)
                .isEqualTo(75);


        // when aiMessage2 is added and aiMessage has to be evicted
        AiMessage aiMessage2 = AiMessage.from("2 + 2 = 4, 3 + 3 = 6");
        int aiMessage2Tokens = TOKENIZER.estimateTokenCountInMessage(aiMessage2);
        assertThat(aiMessage2Tokens).isEqualTo(20);
        chatMemory.add(aiMessage2);

        // then orphan toolExecutionResultMessage1 and toolExecutionResultMessage2 are evicted together with aiMessage
        assertThat(chatMemory.messages()).containsExactly(aiMessage2);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + aiMessage2Tokens)
                .isEqualTo(23);
    }

    @Test
    void should_evict_multiple_orphan_ToolExecutionResultMessages_when_evicting_AiMessage_with_ToolExecutionRequests_when_SystemMessage_is_present() {

        // given
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(96, TOKENIZER);


        // when
        SystemMessage systemMessage = SystemMessage.from("Use calculator for math questions");
        int systemMessageTokens = TOKENIZER.estimateTokenCountInMessage(systemMessage);
        assertThat(systemMessageTokens).isEqualTo(9);
        chatMemory.add(systemMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens)
                .isEqualTo(12);


        // when
        UserMessage userMessage = UserMessage.from("How much is 2+2 and 3+3?");
        int userMessageTokens = TOKENIZER.estimateTokenCountInMessage(userMessage);
        assertThat(userMessageTokens).isEqualTo(17);
        chatMemory.add(userMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens + userMessageTokens)
                .isEqualTo(29);


        // when
        ToolExecutionRequest toolExecutionRequest1 = ToolExecutionRequest.builder()
                .id("1")
                .name("calculator")
                .arguments("{ \"a\": 2, \"b\": 2 }")
                .build();
        ToolExecutionRequest toolExecutionRequest2 = ToolExecutionRequest.builder()
                .id("2")
                .name("calculator")
                .arguments("{ \"a\": 3, \"b\": 3 }")
                .build();
        AiMessage aiMessage = AiMessage.from(toolExecutionRequest1, toolExecutionRequest2);
        int aiMessageTokens = TOKENIZER.estimateTokenCountInMessage(aiMessage);
        assertThat(aiMessageTokens).isEqualTo(62);
        chatMemory.add(aiMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage, aiMessage);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens + userMessageTokens + aiMessageTokens)
                .isEqualTo(91);


        // when
        ToolExecutionResultMessage toolExecutionResultMessage1 =
                ToolExecutionResultMessage.from(toolExecutionRequest1, "4");
        int toolExecutionResultMessage1Tokens = TOKENIZER.estimateTokenCountInMessage(toolExecutionResultMessage1);
        assertThat(toolExecutionResultMessage1Tokens).isEqualTo(5);
        chatMemory.add(toolExecutionResultMessage1);

        // then
        assertThat(chatMemory.messages())
                .containsExactly(systemMessage, userMessage, aiMessage, toolExecutionResultMessage1);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens + userMessageTokens + aiMessageTokens + toolExecutionResultMessage1Tokens)
                .isEqualTo(96);


        // when
        ToolExecutionResultMessage toolExecutionResultMessage2 =
                ToolExecutionResultMessage.from(toolExecutionRequest2, "6");
        int toolExecutionResultMessage2Tokens = TOKENIZER.estimateTokenCountInMessage(toolExecutionResultMessage2);
        assertThat(toolExecutionResultMessage2Tokens).isEqualTo(5);
        chatMemory.add(toolExecutionResultMessage2);

        // then
        assertThat(chatMemory.messages())
                .containsExactly(systemMessage, aiMessage, toolExecutionResultMessage1, toolExecutionResultMessage2);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens + aiMessageTokens + toolExecutionResultMessage1Tokens + toolExecutionResultMessage2Tokens)
                .isEqualTo(84);


        // when aiMessage2 is added and aiMessage has to be evicted
        AiMessage aiMessage2 = AiMessage.from("2 + 2 = 4, 3 + 3 = 6");
        int aiMessage2Tokens = TOKENIZER.estimateTokenCountInMessage(aiMessage2);
        assertThat(aiMessage2Tokens).isEqualTo(20);
        chatMemory.add(aiMessage2);

        // then orphan toolExecutionResultMessage1 and toolExecutionResultMessage2 are evicted together with aiMessage
        assertThat(chatMemory.messages()).containsExactly(systemMessage, aiMessage2);
        assertThat(TOKENIZER.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens + aiMessage2Tokens)
                .isEqualTo(32);
    }
}