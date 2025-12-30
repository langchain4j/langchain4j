package dev.langchain4j.memory.chat;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.TestUtils.aiMessageWithTokens;
import static dev.langchain4j.internal.TestUtils.systemMessageWithTokens;
import static dev.langchain4j.internal.TestUtils.userMessageWithTokens;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static java.util.Collections.singletonList;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import java.util.function.Function;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class TokenWindowChatMemoryTest implements WithAssertions {

    private static final TokenCountEstimator TOKEN_COUNT_ESTIMATOR = new OpenAiTokenCountEstimator(GPT_4_O_MINI);
    private static final int EXTRA_TOKENS_PER_REQUEST = 3;

    @Test
    void id() {
        {
            ChatMemory chatMemory = TokenWindowChatMemory.builder()
                    .maxTokens(2, TOKEN_COUNT_ESTIMATOR)
                    .build();
            assertThat(chatMemory.id()).isEqualTo("default");
        }
        {
            ChatMemory chatMemory = TokenWindowChatMemory.builder()
                    .id("abc")
                    .maxTokens(2, TOKEN_COUNT_ESTIMATOR)
                    .build();
            assertThat(chatMemory.id()).isEqualTo("abc");
        }
    }

    @Test
    void store_and_clear() {

        ChatMessage m1 = userMessage("hello");
        ChatMessage m2 = userMessage("world");
        ChatMessage m3 = userMessage("banana");

        int m1tokens = TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(m1);
        int m2tokens = TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(m2);

        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(singletonList(m1)))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + m1tokens);

        ChatMemory chatMemory = TokenWindowChatMemory.builder()
                .maxTokens(EXTRA_TOKENS_PER_REQUEST + m1tokens + m2tokens, TOKEN_COUNT_ESTIMATOR)
                .build();

        chatMemory.add(m1);
        chatMemory.add(m2);

        assertThat(chatMemory.messages()).containsExactly(m1, m2);

        chatMemory.add(m3);

        assertThat(chatMemory.messages()).containsExactly(m2, m3);

        chatMemory.clear();
        // idempotent
        chatMemory.clear();

        assertThat(chatMemory.messages()).isEmpty();
    }

    @Test
    void should_keep_specified_number_of_tokens_in_chat_memory() {

        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(33, TOKEN_COUNT_ESTIMATOR);

        UserMessage firstUserMessage = userMessageWithTokens(10);
        chatMemory.add(firstUserMessage);
        assertThat(chatMemory.messages()).containsExactly(firstUserMessage);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(
                        EXTRA_TOKENS_PER_REQUEST + 10 // firstUserMessage
                        );

        AiMessage firstAiMessage = aiMessageWithTokens(10);
        chatMemory.add(firstAiMessage);
        assertThat(chatMemory.messages()).containsExactly(firstUserMessage, firstAiMessage);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(
                        EXTRA_TOKENS_PER_REQUEST
                                + 10 // firstUserMessage
                                + 10 // firstAiMessage
                        );

        UserMessage secondUserMessage = userMessageWithTokens(10);
        chatMemory.add(secondUserMessage);
        assertThat(chatMemory.messages()).containsExactly(firstUserMessage, firstAiMessage, secondUserMessage);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(
                        EXTRA_TOKENS_PER_REQUEST
                                + 10 // firstUserMessage
                                + 10 // firstAiMessage
                                + 10 // secondUserMessage
                        );

        AiMessage secondAiMessage = aiMessageWithTokens(10);
        chatMemory.add(secondAiMessage);
        assertThat(chatMemory.messages())
                .containsExactly(
                        // firstUserMessage was evicted
                        firstAiMessage, secondUserMessage, secondAiMessage);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(
                        EXTRA_TOKENS_PER_REQUEST
                                + 10 // firstAiMessage
                                + 10 // secondUserMessage
                                + 10 // secondAiMessage
                        );
    }

    @Test
    void should_not_evict_system_message_from_chat_memory() {

        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(33, TOKEN_COUNT_ESTIMATOR);

        SystemMessage systemMessage = systemMessageWithTokens(10);
        chatMemory.add(systemMessage);

        UserMessage firstUserMessage = userMessageWithTokens(10);
        chatMemory.add(firstUserMessage);

        AiMessage firstAiMessage = aiMessageWithTokens(10);
        chatMemory.add(firstAiMessage);

        assertThat(chatMemory.messages()).containsExactly(systemMessage, firstUserMessage, firstAiMessage);

        UserMessage secondUserMessage = userMessageWithTokens(10);
        chatMemory.add(secondUserMessage);
        assertThat(chatMemory.messages())
                .containsExactly(
                        systemMessage,
                        // firstUserMessage was evicted
                        firstAiMessage,
                        secondUserMessage);

        AiMessage secondAiMessage = aiMessageWithTokens(10);
        chatMemory.add(secondAiMessage);
        assertThat(chatMemory.messages())
                .containsExactly(
                        systemMessage,
                        // firstAiMessage was evicted
                        secondUserMessage,
                        secondAiMessage);
    }

    @Test
    void should_keep_only_the_latest_system_message_in_chat_memory() {

        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(40, TOKEN_COUNT_ESTIMATOR);

        SystemMessage firstSystemMessage = systemMessage("You are a helpful assistant");
        chatMemory.add(firstSystemMessage);

        UserMessage firstUserMessage = userMessageWithTokens(10);
        chatMemory.add(firstUserMessage);

        AiMessage firstAiMessage = aiMessageWithTokens(10);
        chatMemory.add(firstAiMessage);

        assertThat(chatMemory.messages()).containsExactly(firstSystemMessage, firstUserMessage, firstAiMessage);

        SystemMessage secondSystemMessage = systemMessage("You are an unhelpful assistant");
        chatMemory.add(secondSystemMessage);
        assertThat(chatMemory.messages())
                .containsExactly(
                        // firstSystemMessage was evicted
                        firstUserMessage, firstAiMessage, secondSystemMessage);
    }

    @Test
    void should_not_add_the_same_system_message_to_chat_memory_if_it_is_already_there() {

        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(33, TOKEN_COUNT_ESTIMATOR);

        SystemMessage systemMessage = systemMessageWithTokens(10);
        chatMemory.add(systemMessage);

        UserMessage userMessage = userMessageWithTokens(10);
        chatMemory.add(userMessage);

        AiMessage aiMessage = aiMessageWithTokens(10);
        chatMemory.add(aiMessage);

        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage, aiMessage);

        chatMemory.add(systemMessage);

        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage, aiMessage);
    }

    @Test
    void should_evict_orphan_ToolExecutionResultMessage_when_evicting_AiMessage_with_ToolExecutionRequest() {

        // given
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(39, TOKEN_COUNT_ESTIMATOR);

        // when
        UserMessage userMessage = UserMessage.from("How much is 2+2?");
        int userMessageTokens = TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(userMessage);
        assertThat(userMessageTokens).isEqualTo(12);
        chatMemory.add(userMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(userMessage);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + userMessageTokens)
                .isEqualTo(15);

        // when
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("calculator")
                .arguments("{ \"a\": 2, \"b\": 2 }")
                .build();
        AiMessage aiMessage = AiMessage.from(toolExecutionRequest);
        int aiMessageTokens = TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(aiMessage);
        assertThat(aiMessageTokens).isEqualTo(24);
        chatMemory.add(aiMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(userMessage, aiMessage);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + userMessageTokens + aiMessageTokens)
                .isEqualTo(39);

        // when
        ToolExecutionResultMessage toolExecutionResultMessage =
                ToolExecutionResultMessage.from(toolExecutionRequest, "4");
        int toolExecutionResultMessageTokens =
                TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(toolExecutionResultMessage);
        assertThat(toolExecutionResultMessageTokens).isEqualTo(5);
        chatMemory.add(toolExecutionResultMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(aiMessage, toolExecutionResultMessage);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + aiMessageTokens + toolExecutionResultMessageTokens)
                .isEqualTo(32);

        // when new message is added and aiMessage (containing ToolExecutionRequest) has to be evicted
        AiMessage aiMessage2 = AiMessage.from("2 + 2 = 4");
        int aiMessage2Tokens = TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(aiMessage2);
        assertThat(aiMessage2Tokens).isEqualTo(11);
        chatMemory.add(aiMessage2);

        // then orphan toolExecutionResultMessage is evicted together with aiMessage
        assertThat(chatMemory.messages()).containsExactly(aiMessage2);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + aiMessage2Tokens)
                .isEqualTo(14);
    }

    @Test
    void
            should_evict_orphan_ToolExecutionResultMessage_when_evicting_AiMessage_with_ToolExecutionRequest_when_SystemMessage_is_present() {

        // given
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(48, TOKEN_COUNT_ESTIMATOR);

        // when
        SystemMessage systemMessage = SystemMessage.from("Use calculator for math questions");
        int systemMessageTokens = TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(systemMessage);
        assertThat(systemMessageTokens).isEqualTo(9);
        chatMemory.add(systemMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens)
                .isEqualTo(12);

        // when
        UserMessage userMessage = UserMessage.from("How much is 2+2?");
        int userMessageTokens = TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(userMessage);
        assertThat(userMessageTokens).isEqualTo(12);
        chatMemory.add(userMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens + userMessageTokens)
                .isEqualTo(24);

        // when
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("calculator")
                .arguments("{ \"a\": 2, \"b\": 2 }")
                .build();
        AiMessage aiMessage = AiMessage.from(toolExecutionRequest);
        int aiMessageTokens = TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(aiMessage);
        assertThat(aiMessageTokens).isEqualTo(24);
        chatMemory.add(aiMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage, aiMessage);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens + userMessageTokens + aiMessageTokens)
                .isEqualTo(48);

        // when
        ToolExecutionResultMessage toolExecutionResultMessage =
                ToolExecutionResultMessage.from(toolExecutionRequest, "4");
        int toolExecutionResultMessageTokens =
                TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(toolExecutionResultMessage);
        assertThat(toolExecutionResultMessageTokens).isEqualTo(5);
        chatMemory.add(toolExecutionResultMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, aiMessage, toolExecutionResultMessage);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST
                        + systemMessageTokens
                        + aiMessageTokens
                        + toolExecutionResultMessageTokens)
                .isEqualTo(41);

        // when aiMessage2 is added and aiMessage has to be evicted
        AiMessage aiMessage2 = AiMessage.from("2 + 2 = 4");
        int aiMessage2Tokens = TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(aiMessage2);
        assertThat(aiMessage2Tokens).isEqualTo(11);
        chatMemory.add(aiMessage2);

        // then orphan toolExecutionResultMessage is evicted together with aiMessage
        assertThat(chatMemory.messages()).containsExactly(systemMessage, aiMessage2);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens + aiMessage2Tokens)
                .isEqualTo(23);
    }

    @Test
    void
            should_evict_orphan_ToolExecutionResultMessage_when_evicting_AiMessage_with_ToolExecutionRequest_when_SystemMessage_is_present_2() {

        // given chat memory
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(36, TOKEN_COUNT_ESTIMATOR);

        // when
        SystemMessage systemMessage = SystemMessage.from("Use calculator for math questions");
        int systemMessageTokens = TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(systemMessage);
        assertThat(systemMessageTokens).isEqualTo(9);
        chatMemory.add(systemMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens)
                .isEqualTo(12);

        // when
        UserMessage userMessage = UserMessage.from("How much is 2+2?");
        int userMessageTokens = TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(userMessage);
        assertThat(userMessageTokens).isEqualTo(12);
        chatMemory.add(userMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens + userMessageTokens)
                .isEqualTo(24);

        // when
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("calculator")
                .arguments("{ \"a\": 2, \"b\": 2 }")
                .build();
        AiMessage aiMessage = AiMessage.from(toolExecutionRequest);
        int aiMessageTokens = TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(aiMessage);
        assertThat(aiMessageTokens).isEqualTo(24);
        chatMemory.add(aiMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, aiMessage);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens + aiMessageTokens)
                .isEqualTo(36);

        // when toolExecutionResultMessage is added and aiMessage has to be evicted
        ToolExecutionResultMessage toolExecutionResultMessage =
                ToolExecutionResultMessage.from(toolExecutionRequest, "4");
        int toolExecutionResultMessageTokens =
                TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(toolExecutionResultMessage);
        assertThat(toolExecutionResultMessageTokens).isEqualTo(5);
        chatMemory.add(toolExecutionResultMessage);

        // then orphan toolExecutionResultMessage is evicted together with aiMessage
        assertThat(chatMemory.messages()).containsExactly(systemMessage);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens)
                .isEqualTo(12);
    }

    @Test
    void should_evict_multiple_orphan_ToolExecutionResultMessages_when_evicting_AiMessage_with_ToolExecutionRequests() {

        // given
        int maxTokens = 82;

        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(maxTokens, TOKEN_COUNT_ESTIMATOR);

        // when
        UserMessage userMessage = UserMessage.from("How much is 2+2 and 3+3?");
        int userMessageTokens = TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(userMessage);
        assertThat(userMessageTokens).isEqualTo(17);
        chatMemory.add(userMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(userMessage);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
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
        int aiMessageTokens = TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(aiMessage);
        assertThat(aiMessageTokens).isEqualTo(57);
        chatMemory.add(aiMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(userMessage, aiMessage);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + userMessageTokens + aiMessageTokens)
                .isEqualTo(77);

        // when
        ToolExecutionResultMessage toolExecutionResultMessage1 =
                ToolExecutionResultMessage.from(toolExecutionRequest1, "4");
        int toolExecutionResultMessage1Tokens =
                TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(toolExecutionResultMessage1);
        assertThat(toolExecutionResultMessage1Tokens).isEqualTo(5);
        chatMemory.add(toolExecutionResultMessage1);

        // then
        assertThat(chatMemory.messages()).containsExactly(userMessage, aiMessage, toolExecutionResultMessage1);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST
                        + userMessageTokens
                        + aiMessageTokens
                        + toolExecutionResultMessage1Tokens)
                .isEqualTo(maxTokens);

        // when
        ToolExecutionResultMessage toolExecutionResultMessage2 =
                ToolExecutionResultMessage.from(toolExecutionRequest2, "6");
        int toolExecutionResultMessage2Tokens =
                TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(toolExecutionResultMessage2);
        assertThat(toolExecutionResultMessage2Tokens).isEqualTo(5);
        chatMemory.add(toolExecutionResultMessage2);

        // then
        assertThat(chatMemory.messages())
                .containsExactly(aiMessage, toolExecutionResultMessage1, toolExecutionResultMessage2);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST
                        + aiMessageTokens
                        + toolExecutionResultMessage1Tokens
                        + toolExecutionResultMessage2Tokens)
                .isEqualTo(70);

        // when aiMessage2 is added and aiMessage has to be evicted
        AiMessage aiMessage2 = AiMessage.from("2 + 2 = 4, 3 + 3 = 6");
        int aiMessage2Tokens = TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(aiMessage2);
        assertThat(aiMessage2Tokens).isEqualTo(20);
        chatMemory.add(aiMessage2);

        // then orphan toolExecutionResultMessage1 and toolExecutionResultMessage2 are evicted together with aiMessage
        assertThat(chatMemory.messages()).containsExactly(aiMessage2);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + aiMessage2Tokens)
                .isEqualTo(23);
    }

    @Test
    void
            should_evict_multiple_orphan_ToolExecutionResultMessages_when_evicting_AiMessage_with_ToolExecutionRequests_when_SystemMessage_is_present() {

        // given
        int maxTokens = 91;

        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(maxTokens, TOKEN_COUNT_ESTIMATOR);

        // when
        SystemMessage systemMessage = SystemMessage.from("Use calculator for math questions");
        int systemMessageTokens = TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(systemMessage);
        assertThat(systemMessageTokens).isEqualTo(9);
        chatMemory.add(systemMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens)
                .isEqualTo(12);

        // when
        UserMessage userMessage = UserMessage.from("How much is 2+2 and 3+3?");
        int userMessageTokens = TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(userMessage);
        assertThat(userMessageTokens).isEqualTo(17);
        chatMemory.add(userMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
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
        int aiMessageTokens = TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(aiMessage);
        assertThat(aiMessageTokens).isEqualTo(57);
        chatMemory.add(aiMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage, aiMessage);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens + userMessageTokens + aiMessageTokens)
                .isEqualTo(86);

        // when
        ToolExecutionResultMessage toolExecutionResultMessage1 =
                ToolExecutionResultMessage.from(toolExecutionRequest1, "4");
        int toolExecutionResultMessage1Tokens =
                TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(toolExecutionResultMessage1);
        assertThat(toolExecutionResultMessage1Tokens).isEqualTo(5);
        chatMemory.add(toolExecutionResultMessage1);

        // then
        assertThat(chatMemory.messages())
                .containsExactly(systemMessage, userMessage, aiMessage, toolExecutionResultMessage1);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST
                        + systemMessageTokens
                        + userMessageTokens
                        + aiMessageTokens
                        + toolExecutionResultMessage1Tokens)
                .isEqualTo(maxTokens);

        // when
        ToolExecutionResultMessage toolExecutionResultMessage2 =
                ToolExecutionResultMessage.from(toolExecutionRequest2, "6");
        int toolExecutionResultMessage2Tokens =
                TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(toolExecutionResultMessage2);
        assertThat(toolExecutionResultMessage2Tokens).isEqualTo(5);
        chatMemory.add(toolExecutionResultMessage2);

        // then
        assertThat(chatMemory.messages())
                .containsExactly(systemMessage, aiMessage, toolExecutionResultMessage1, toolExecutionResultMessage2);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST
                        + systemMessageTokens
                        + aiMessageTokens
                        + toolExecutionResultMessage1Tokens
                        + toolExecutionResultMessage2Tokens)
                .isEqualTo(79);

        // when aiMessage2 is added and aiMessage has to be evicted
        AiMessage aiMessage2 = AiMessage.from("2 + 2 = 4, 3 + 3 = 6");
        int aiMessage2Tokens = TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessage(aiMessage2);
        assertThat(aiMessage2Tokens).isEqualTo(20);
        chatMemory.add(aiMessage2);

        // then orphan toolExecutionResultMessage1 and toolExecutionResultMessage2 are evicted together with aiMessage
        assertThat(chatMemory.messages()).containsExactly(systemMessage, aiMessage2);
        assertThat(TOKEN_COUNT_ESTIMATOR.estimateTokenCountInMessages(chatMemory.messages()))
                .isEqualTo(EXTRA_TOKENS_PER_REQUEST + systemMessageTokens + aiMessage2Tokens)
                .isEqualTo(32);
    }

    @Test
    void should_work_even_if_the_first_message_exceeds_max_tokens() {
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(
                2, new OpenAiTokenCountEstimator(OpenAiChatModelName.GPT_3_5_TURBO));

        chatMemory.add(userMessageWithTokens(25));
        chatMemory.add(aiMessageWithTokens(10));

        chatMemory.add(userMessageWithTokens(25));
        chatMemory.add(aiMessageWithTokens(10));
    }

    @Test
    void should_not_fail_even_with_just_system_message() {
        ChatMemory chatMemory = TokenWindowChatMemory.withMaxTokens(
                2, new OpenAiTokenCountEstimator(OpenAiChatModelName.GPT_3_5_TURBO));
        chatMemory.add(systemMessageWithTokens(10));
    }

    @Test
    void dynamic_max_tokens_behavior() {
        // Dynamic maxTokens function: different IDs have different token windows
        Function<Object, Integer> dynamicMaxTokens = id -> {
            if ("short".equals(id)) return 33;
            if ("long".equals(id)) return 60;
            return 45; // default
        };

        // Define messages with token counts
        var msgA = userMessageWithTokens(10);
        var msgB = aiMessageWithTokens(10);
        var msgC = userMessageWithTokens(10);
        var msgD = aiMessageWithTokens(10);
        var msgE = userMessageWithTokens(10);

        // shortMemory: maxTokens = 33
        TokenWindowChatMemory shortMemory = TokenWindowChatMemory.builder()
                .id("short")
                .dynamicMaxTokens(dynamicMaxTokens, TOKEN_COUNT_ESTIMATOR)
                .build();

        shortMemory.add(msgA);
        shortMemory.add(msgB);
        shortMemory.add(msgC);
        // Adding msgD will trigger eviction of the oldest messages
        shortMemory.add(msgD);

        // Keep the most recent messages that fit within maxTokens
        assertThat(shortMemory.messages()).containsExactly(msgB, msgC, msgD);

        // longMemory: maxTokens = 60
        TokenWindowChatMemory longMemory = TokenWindowChatMemory.builder()
                .id("long")
                .dynamicMaxTokens(dynamicMaxTokens, TOKEN_COUNT_ESTIMATOR)
                .build();

        longMemory.add(msgA);
        longMemory.add(msgB);
        longMemory.add(msgC);
        longMemory.add(msgD);
        longMemory.add(msgE);

        // Total tokens do not exceed 60, all messages are retained
        assertThat(longMemory.messages()).containsExactly(msgA, msgB, msgC, msgD, msgE);

        // Default ID: maxTokens = 45
        TokenWindowChatMemory defaultMemory = TokenWindowChatMemory.builder()
                .id("other")
                .dynamicMaxTokens(dynamicMaxTokens, TOKEN_COUNT_ESTIMATOR)
                .build();

        defaultMemory.add(msgA);
        defaultMemory.add(msgB);
        defaultMemory.add(msgC);
        defaultMemory.add(msgD);

        // Keep the most recent messages that fit within maxTokens
        assertThat(defaultMemory.messages()).containsExactly(msgA, msgB, msgC, msgD);
    }

    @Test
    void dynamic_max_tokens_can_change_for_same_id() {
        // Dynamic maxTokens, can be modified during the test
        int[] currentMaxTokens = {33}; // initial window size
        Function<Object, Integer> dynamicMaxTokens = id -> currentMaxTokens[0];

        // Create chat memory
        TokenWindowChatMemory memory = TokenWindowChatMemory.builder()
                .id("same-id")
                .dynamicMaxTokens(dynamicMaxTokens, TOKEN_COUNT_ESTIMATOR)
                .build();

        // Define messages with token counts
        var msgA = userMessageWithTokens(10);
        var msgB = aiMessageWithTokens(10);
        var msgC = userMessageWithTokens(10);
        var msgD = aiMessageWithTokens(10);
        var msgE = userMessageWithTokens(10);

        // Add the first three messages
        memory.add(msgA);
        memory.add(msgB);
        memory.add(msgC);

        assertThat(memory.messages()).containsExactly(msgA, msgB, msgC);

        // Add the fourth message, triggering eviction
        memory.add(msgD);
        assertThat(memory.messages()).containsExactly(msgB, msgC, msgD);

        // Increase maxTokens = 43 and add a new message; no eviction triggered
        currentMaxTokens[0] = 43;
        memory.add(msgE);
        assertThat(memory.messages()).containsExactly(msgB, msgC, msgD, msgE);

        // Decrease maxTokens = 33, which automatically evicts the oldest messages
        currentMaxTokens[0] = 33;
        var messagesAfterShrink = memory.messages();
        assertThat(messagesAfterShrink)
                .containsExactly(msgC, msgD, msgE); // Keep the most recent messages within token limit
    }

    @Test
    void system_message_first_enabled() {
        TokenWindowChatMemory chatMemory = TokenWindowChatMemory.builder()
                .maxTokens(100, TOKEN_COUNT_ESTIMATOR)
                .alwaysKeepSystemMessageFirst(true)
                .build();

        SystemMessage systemMessage = systemMessageWithTokens(10);
        chatMemory.add(systemMessage);

        UserMessage userMessage = userMessageWithTokens(10);
        chatMemory.add(userMessage);

        AiMessage aiMessage = aiMessageWithTokens(10);
        chatMemory.add(aiMessage);

        // System message should be at the beginning
        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage, aiMessage);

        chatMemory = TokenWindowChatMemory.builder()
                .maxTokens(100, TOKEN_COUNT_ESTIMATOR)
                .alwaysKeepSystemMessageFirst(true)
                .build();

        chatMemory.add(userMessage);
        chatMemory.add(systemMessage);
        chatMemory.add(aiMessage);

        // System message should be at the beginning
        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage, aiMessage);
    }

    @Test
    void system_message_first_disabled() {
        TokenWindowChatMemory chatMemory = TokenWindowChatMemory.builder()
                .maxTokens(100, TOKEN_COUNT_ESTIMATOR)
                .alwaysKeepSystemMessageFirst(false)
                .build();

        SystemMessage systemMessage = systemMessageWithTokens(10);
        chatMemory.add(systemMessage);

        UserMessage userMessage = userMessageWithTokens(10);
        chatMemory.add(userMessage);

        AiMessage aiMessage = aiMessageWithTokens(10);
        chatMemory.add(aiMessage);

        // System message should be at the beginning
        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage, aiMessage);

        chatMemory = TokenWindowChatMemory.builder()
                .maxTokens(100, TOKEN_COUNT_ESTIMATOR)
                .alwaysKeepSystemMessageFirst(false)
                .build();

        chatMemory.add(userMessage);
        chatMemory.add(systemMessage);
        chatMemory.add(aiMessage);

        // System message should NOT be at the beginning
        assertThat(chatMemory.messages()).containsExactly(userMessage, systemMessage, aiMessage);
    }

    @Test
    void system_message_first_with_message_eviction() {
        ChatMemory chatMemory = TokenWindowChatMemory.builder()
                .maxTokens(35, TOKEN_COUNT_ESTIMATOR)
                .alwaysKeepSystemMessageFirst(true)
                .build();

        SystemMessage systemMessage = systemMessageWithTokens(10);
        chatMemory.add(systemMessage);

        UserMessage msg1 = userMessageWithTokens(10);
        chatMemory.add(msg1);

        UserMessage msg2 = userMessageWithTokens(10);
        chatMemory.add(msg2);

        // At capacity: systemMessage, msg1, msg2
        assertThat(chatMemory.messages()).containsExactly(systemMessage, msg1, msg2);

        UserMessage msg3 = userMessageWithTokens(10);
        chatMemory.add(msg3);

        // msg1 should be evicted, systemMessage should remain at the beginning
        assertThat(chatMemory.messages()).containsExactly(systemMessage, msg2, msg3);
    }
}
