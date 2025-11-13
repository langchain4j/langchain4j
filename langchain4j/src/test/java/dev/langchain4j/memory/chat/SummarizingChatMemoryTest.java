package dev.langchain4j.memory.chat;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class SummarizingChatMemoryTest implements WithAssertions {
    @ParameterizedTest
    @MethodSource("models")
    void id(ChatModel chatModel) {
        {
            ChatMemory chatMemory = SummarizingChatMemory.builder()
                    .maxMessages(4)
                    .defaultGenerateSummaryFunction(chatModel)
                    .maxMessagesToSummarize(2)
                    .build();
            assertThat(chatMemory.id()).isEqualTo("default");
        }
        {
            ChatMemory chatMemory = SummarizingChatMemory.builder()
                    .id("abc")
                    .maxMessages(4)
                    .defaultGenerateSummaryFunction(chatModel)
                    .maxMessagesToSummarize(2)
                    .build();
            assertThat(chatMemory.id()).isEqualTo("abc");
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    void store_and_clear(ChatModel chatModel) {
        // Create a summarizing chat memory instance
        ChatMemory chatMemory = SummarizingChatMemory.builder()
                .maxMessages(4) // Keep at most 4 messages
                .defaultGenerateSummaryFunction(chatModel)
                .maxMessagesToSummarize(2) // Trigger summarization when exceeding 2 messages
                .build();

        // Add the first three messages
        chatMemory.add(userMessage("hello"));
        chatMemory.add(userMessage("world"));
        chatMemory.add(userMessage("I am lisi"));

        // Verify that the first three messages are still retained
        assertThat(chatMemory.messages())
                .usingRecursiveFieldByFieldElementComparatorIgnoringFields("id", "timestamp", "createdAt")
                .containsExactly(userMessage("hello"), userMessage("world"), userMessage("I am lisi"));

        chatMemory.add(userMessage("who are you"));

        // The first three messages might have been summarized; verify the message list after summarization
        List<ChatMessage> messagesAfterSummarize = chatMemory.messages();
        assertThat(messagesAfterSummarize.size()).isEqualTo(4); // Ensure total count remains equal to maxMessages

        // Add a fifth message; summarization logic may replace or summarize older messages instead of appending
        // directly
        chatMemory.add(userMessage("I am wangwu"));

        // Verify that the last message is NOT the raw "I am wangwu" (it should have been summarized or excluded)
        assertThat(messagesAfterSummarize.get(messagesAfterSummarize.size() - 1))
                .usingRecursiveComparison()
                .ignoringFields("id", "timestamp", "createdAt")
                .isNotEqualTo(userMessage("I am wangwu"));

        // Clear the memory
        chatMemory.clear();
        chatMemory.clear(); // idempotent operation

        assertThat(chatMemory.messages()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_summarize_messages_when_threshold_exceeded(ChatModel chatModel) {

        // Create a chat memory instance with summarization capability
        ChatMemory chatMemory = SummarizingChatMemory.builder()
                .maxMessages(4) // Keep at most 4 messages
                .defaultGenerateSummaryFunction(chatModel)
                .maxMessagesToSummarize(2) // Summarization is triggered only when the threshold is exceeded
                .build();

        // Add the first four messages — no summarization occurs yet
        UserMessage m1 = userMessage("hello");
        UserMessage m2 = userMessage("world");
        UserMessage m3 = userMessage("I am lisi");
        UserMessage m4 = userMessage("who are you");

        chatMemory.add(m1);
        chatMemory.add(m2);
        chatMemory.add(m3);
        chatMemory.add(m4);

        // Message order remains FIFO, with no summarization applied
        List<ChatMessage> afterFourth = chatMemory.messages();
        assertThat(afterFourth).containsExactly(m1, m2, m3, m4);

        // Add a fifth message, exceeding maxMessages and triggering summarization
        UserMessage m5 = userMessage("I am wangwu");
        chatMemory.add(m5);

        List<ChatMessage> afterFifth = chatMemory.messages();
        assertThat(afterFifth).hasSize(4);

        // The first message is now a summary message, which does not equal any of the original messages
        assertThat(afterFifth.get(0))
                .usingRecursiveComparison()
                .ignoringFields("id", "timestamp", "createdAt")
                .isNotEqualTo(m1)
                .isNotEqualTo(m2)
                .isNotEqualTo(m3)
                .isNotEqualTo(m4)
                .isNotEqualTo(m5);

        // The remaining part of the queue contains the three most recent original messages, preserved in FIFO order
        assertThat(afterFifth.subList(1, 4)).containsExactly(m3, m4, m5); // Note: these are m3, m4, and m5

        // Clear the memory
        chatMemory.clear();
        chatMemory.clear(); // idempotent
        assertThat(chatMemory.messages()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_not_evict_system_message_from_chat_memory(ChatModel chatModel) {

        // Create a chat memory instance with summarization capability
        ChatMemory chatMemory = SummarizingChatMemory.builder()
                .maxMessages(4) // Keep at most 4 messages
                .defaultGenerateSummaryFunction(chatModel)
                .maxMessagesToSummarize(2) // Summarization is triggered only when the threshold is exceeded
                .build();

        // Add a system message
        SystemMessage systemMessage = systemMessage("You are a helpful assistant");
        chatMemory.add(systemMessage);

        // Add several regular messages
        UserMessage m1 = userMessage("Hello");
        chatMemory.add(m1);

        AiMessage m2 = aiMessage("Hi, how can I help you?");
        chatMemory.add(m2);

        // Verify message order: the system message should be at the front
        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).containsExactly(systemMessage, m1, m2);

        // Add a fourth message — no summarization triggered yet
        UserMessage m3 = userMessage("Tell me a joke");
        chatMemory.add(m3);

        messages = chatMemory.messages();
        assertThat(messages).containsExactly(systemMessage, m1, m2, m3);

        // Add a fifth message, which triggers summarization
        UserMessage m4 = userMessage("Another message");
        chatMemory.add(m4);

        messages = chatMemory.messages();
        assertThat(messages).hasSize(4);

        // The first message must remain the system message
        assertThat(messages.get(0)).isEqualTo(systemMessage);

        // The second message should be a summary message
        ChatMessage summaryMessage = messages.get(1);
        assertThat(summaryMessage)
                .usingRecursiveComparison()
                .ignoringFields("id", "timestamp", "createdAt")
                .isNotEqualTo(m1)
                .isNotEqualTo(m2)
                .isNotEqualTo(m3)
                .isNotEqualTo(m4);

        // The remaining two messages are the most recent original messages in FIFO order
        assertThat(messages.subList(2, 4)).containsExactly(m3, m4);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_keep_only_the_latest_system_message_in_chat_memory(ChatModel chatModel) {

        ChatMemory chatMemory = SummarizingChatMemory.builder()
                .maxMessages(4)
                .defaultGenerateSummaryFunction(chatModel)
                .maxMessagesToSummarize(2)
                .build();

        SystemMessage sys1 = systemMessage("You are a helpful assistant");
        chatMemory.add(sys1);

        UserMessage u1 = userMessage("Hello");
        AiMessage a1 = aiMessage("Hi, how can I help you?");
        chatMemory.add(u1);
        chatMemory.add(a1);

        // Verify message order
        assertThat(chatMemory.messages()).containsExactly(sys1, u1, a1);

        // Add a second system message — it should replace the first one
        SystemMessage sys2 = systemMessage("You are an unhelpful assistant 2");
        chatMemory.add(sys2);

        // Message order becomes: u1, a1, sys2
        assertThat(chatMemory.messages()).containsExactly(u1, a1, sys2);

        // Add a fifth message; since there's only one system message now, summarization may not be triggered yet
        UserMessage u2 = userMessage("Tell me a joke");
        chatMemory.add(u2);

        List<ChatMessage> msgs = chatMemory.messages();
        assertThat(msgs).hasSize(4);

        // The system message should be the second-to-last
        assertThat(msgs.get(msgs.size() - 2)).isEqualTo(sys2);

        // Add a sixth message (total of 5 messages before summarization), which triggers summarization
        AiMessage a2 = aiMessage("you are big dan !");
        chatMemory.add(a2);
        msgs = chatMemory.messages();
        // After summarization, total count should be 4
        assertThat(msgs).hasSize(4);

        // The first message should be a summary message
        ChatMessage summary = msgs.get(0);
        assertThat(summary)
                .usingRecursiveComparison()
                .ignoringFields("id", "timestamp", "createdAt")
                .isNotEqualTo(sys1)
                .isNotEqualTo(a1)
                .isNotEqualTo(u1);

        // The last two messages should be the most recent original messages
        assertThat(msgs.subList(2, 4)).containsExactly(u2, a2);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_not_add_the_same_system_message_to_chat_memory_if_it_is_already_there(ChatModel chatModel) {

        ChatMemory chatMemory = SummarizingChatMemory.builder()
                .maxMessages(4)
                .defaultGenerateSummaryFunction(chatModel)
                .maxMessagesToSummarize(2)
                .build();

        // Add a system message
        SystemMessage sys = systemMessage("You are a helpful assistant");
        chatMemory.add(sys);

        // Add some regular messages
        UserMessage u1 = userMessage("Hello");
        AiMessage a1 = aiMessage("Hi, how can I help you?");
        chatMemory.add(u1);
        chatMemory.add(a1);

        // Verify message order
        assertThat(chatMemory.messages()).containsExactly(sys, u1, a1);

        // Adding the same system message again should have no effect (no duplication)
        chatMemory.add(sys);
        assertThat(chatMemory.messages()).containsExactly(sys, u1, a1);

        // Add a fourth message — summarization is not triggered yet
        UserMessage u2 = userMessage("Tell me a joke");
        chatMemory.add(u2);
        List<ChatMessage> msgs = chatMemory.messages();
        assertThat(msgs).hasSize(4);
        // The system message remains at the front
        assertThat(msgs.get(0)).isEqualTo(sys);

        // Add a fifth message, which triggers summarization
        AiMessage a2 = aiMessage("Why did the chicken cross the road?");
        chatMemory.add(a2);
        msgs = chatMemory.messages();
        // After summarization, the queue size should still equal maxMessages
        assertThat(msgs).hasSize(4);

        // The first message is the system message; the second is the summary
        ChatMessage summary = msgs.get(1);
        ChatMessage sysMsg = msgs.get(0);
        assertThat(sysMsg).isEqualTo(sys);
        assertThat(summary)
                .usingRecursiveComparison()
                .ignoringFields("id", "timestamp", "createdAt")
                .isNotEqualTo(sys)
                .isNotEqualTo(u1)
                .isNotEqualTo(a1);

        // The last two messages are the most recent original messages
        assertThat(msgs.subList(2, 4)).containsExactly(u2, a2);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_evict_orphan_ToolExecutionResultMessage_when_evicting_AiMessage_with_ToolExecutionRequest(
            ChatModel chatModel) {

        // Configure maxMessages=3 and summarizeThreshold=2 (>1)
        ChatMemory chatMemory = SummarizingChatMemory.builder()
                .maxMessages(3)
                .defaultGenerateSummaryFunction(chatModel)
                .maxMessagesToSummarize(2) // must be greater than 1
                .build();

        // Add a user message
        UserMessage u1 = userMessage("How much is 2 plus 2?");
        chatMemory.add(u1);
        assertThat(chatMemory.messages()).containsExactly(u1);

        // Add an AI message containing a ToolExecutionRequest
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("1")
                .name("calculator")
                .arguments("{ \"a\": 2, \"b\": 2 }")
                .build();
        AiMessage ai1 = AiMessage.from(request);
        chatMemory.add(ai1);
        List<ChatMessage> msgsAfterAi1 = chatMemory.messages();
        assertThat(msgsAfterAi1).contains(u1, ai1);

        // Add the corresponding ToolExecutionResultMessage
        ToolExecutionResultMessage result1 = ToolExecutionResultMessage.from(request, "4");
        chatMemory.add(result1);
        List<ChatMessage> msgsAfterResult1 = chatMemory.messages();
        assertThat(msgsAfterResult1).containsExactly(u1, ai1, result1);

        // Add a new AI message, which triggers eviction (exceeds maxMessages)
        AiMessage ai2 = aiMessage("2 + 2 = 4 !!!");
        chatMemory.add(ai2);

        List<ChatMessage> finalMsgs = chatMemory.messages();
        // The oldest message (u1) is evicted, and summarization is triggered
        assertThat(finalMsgs).hasSize(2);

        // The first message is a summary message
        ChatMessage summary = finalMsgs.get(0);
        assertThat(summary)
                .isNotEqualTo(u1)
                .isNotEqualTo(ai1)
                .isNotEqualTo(result1)
                .isNotEqualTo(ai2);

        // The last message is the most recent AI message
        assertThat(finalMsgs.subList(1, 2)).containsExactly(ai2);
    }

    @ParameterizedTest
    @MethodSource("models")
    void
            should_evict_orphan_ToolExecutionResultMessage_when_evicting_AiMessage_with_ToolExecutionRequest_when_SystemMessage_is_present(
                    ChatModel chatModel) {

        // Configure summarizeThreshold > 1
        ChatMemory chatMemory = SummarizingChatMemory.builder()
                .maxMessages(4)
                .defaultGenerateSummaryFunction(chatModel)
                .maxMessagesToSummarize(2) // must be greater than 1
                .build();

        // Add a SystemMessage
        SystemMessage sys = systemMessage("Use calculator for math questions");
        chatMemory.add(sys);
        assertThat(chatMemory.messages()).containsExactly(sys);

        // Add a UserMessage
        UserMessage u1 = userMessage("How much is 2+2?");
        chatMemory.add(u1);
        assertThat(chatMemory.messages()).containsExactly(sys, u1);

        // Add an AiMessage containing a ToolExecutionRequest
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("1")
                .name("calculator")
                .arguments("{ \"a\": 2, \"b\": 2 }")
                .build();
        AiMessage ai1 = AiMessage.from(request);
        chatMemory.add(ai1);
        assertThat(chatMemory.messages()).containsExactly(sys, u1, ai1);

        // Add the corresponding ToolExecutionResultMessage
        ToolExecutionResultMessage result1 = ToolExecutionResultMessage.from(request, "4");
        chatMemory.add(result1);

        // SummarizingChatMemory will evict the oldest messages when exceeding capacity,
        // but the SystemMessage must never be evicted
        List<ChatMessage> msgsAfterResult = chatMemory.messages();
        assertThat(msgsAfterResult.get(0)).isEqualTo(sys);
        assertThat(msgsAfterResult.subList(1, msgsAfterResult.size())).containsExactly(u1, ai1, result1);

        // Add a new AiMessage, which triggers eviction (exceeds maxMessages with summarizeThreshold=2)
        AiMessage ai2 = aiMessage("2 + 2 = 4 !!!!");
        chatMemory.add(ai2);

        List<ChatMessage> finalMsgs = chatMemory.messages();
        // The messages u1, ai1, and result1 are evicted and replaced by a summary message

        // The first message remains the SystemMessage
        assertThat(finalMsgs.get(0)).isEqualTo(sys);

        // The second message is a summary, which does not equal any of the original messages
        ChatMessage summary = finalMsgs.get(1);
        assertThat(summary)
                .usingRecursiveComparison()
                .ignoringFields("id", "timestamp", "createdAt")
                .isNotEqualTo(u1)
                .isNotEqualTo(ai1)
                .isNotEqualTo(result1)
                .isNotEqualTo(ai2);

        // The last message is the most recent AI message
        assertThat(finalMsgs.get(finalMsgs.size() - 1)).isEqualTo(ai2);
    }

    @ParameterizedTest
    @MethodSource("models")
    void
            should_evict_orphan_ToolExecutionResultMessage_when_evicting_AiMessage_with_ToolExecutionRequest_when_SystemMessage_is_present_2(
                    ChatModel chatModel) {

        // given: chat memory with maxMessages=3, summarizeThreshold=2 (>1)
        ChatMemory chatMemory = SummarizingChatMemory.builder()
                .maxMessages(3)
                .defaultGenerateSummaryFunction(chatModel)
                .maxMessagesToSummarize(2)
                .build();

        // Add a SystemMessage
        SystemMessage sys = systemMessage("Use calculator for math questions");
        chatMemory.add(sys);
        assertThat(chatMemory.messages()).containsExactly(sys);

        // Add a UserMessage
        UserMessage u1 = userMessage("How much is 2+2?");
        chatMemory.add(u1);
        assertThat(chatMemory.messages()).containsExactly(sys, u1);

        // Add an AiMessage containing a ToolExecutionRequest
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("1")
                .name("calculator")
                .arguments("{ \"a\": 2, \"b\": 2 }")
                .build();
        AiMessage ai1 = AiMessage.from(request);
        chatMemory.add(ai1);

        // At this point, message count = 3, which does not exceed maxMessages, so summarization is not triggered
        List<ChatMessage> msgsAfterAi = chatMemory.messages();
        assertThat(msgsAfterAi).containsExactly(sys, u1, ai1);

        // Add the corresponding ToolExecutionResultMessage
        ToolExecutionResultMessage result1 = ToolExecutionResultMessage.from(request, "4");
        chatMemory.add(result1);

        // Message count = 4, exceeding maxMessages, triggering summarization and eviction
        List<ChatMessage> finalMsgs = chatMemory.messages();

        // The SystemMessage remains at the front of the queue
        assertThat(finalMsgs.get(0)).isEqualTo(sys);

        // The remaining messages (after the SystemMessage) should only contain summary messages and the latest
        // messages;
        // no UserMessages or AiMessages with ToolExecutionRequests should remain
        assertThat(finalMsgs.subList(1, finalMsgs.size())).allMatch(msg -> msg instanceof UserMessage);

        // Add a new AiMessage to ensure the latest message is retained
        AiMessage ai2 = aiMessage("2 + 2 = 4");
        chatMemory.add(ai2);
        List<ChatMessage> latestMsgs = chatMemory.messages();

        // The first message is the SystemMessage, and the last message is the newly added ai2
        assertThat(latestMsgs.get(0)).isEqualTo(sys);
        assertThat(latestMsgs.get(latestMsgs.size() - 1)).isEqualTo(ai2);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_evict_multiple_orphan_ToolExecutionResultMessages_when_evicting_AiMessage_with_ToolExecutionRequests(
            ChatModel chatModel) {

        // given: SummarizingChatMemory with maxMessages=4, summarizeThreshold=2 (>1)
        ChatMemory chatMemory = SummarizingChatMemory.builder()
                .maxMessages(4)
                .defaultGenerateSummaryFunction(chatModel)
                .maxMessagesToSummarize(2)
                .build();

        // Add a user message
        UserMessage u1 = userMessage("How much is 2+2 and 3+3?");
        chatMemory.add(u1);
        assertThat(chatMemory.messages()).containsExactly(u1);

        // Add an AiMessage containing two ToolExecutionRequests
        ToolExecutionRequest req1 = ToolExecutionRequest.builder()
                .id("1")
                .name("calculator")
                .arguments("{ \"a\": 2, \"b\": 2 }")
                .build();
        ToolExecutionRequest req2 = ToolExecutionRequest.builder()
                .id("2")
                .name("calculator")
                .arguments("{ \"a\": 3, \"b\": 3 }")
                .build();
        AiMessage ai1 = AiMessage.from(req1, req2);
        chatMemory.add(ai1);
        assertThat(chatMemory.messages()).contains(u1, ai1);

        // Add the corresponding ToolExecutionResultMessages
        ToolExecutionResultMessage result1 = ToolExecutionResultMessage.from(req1, "4");
        ToolExecutionResultMessage result2 = ToolExecutionResultMessage.from(req2, "6");
        chatMemory.add(result1);
        chatMemory.add(result2);

        List<ChatMessage> msgsAfterResults = chatMemory.messages();
        assertThat(msgsAfterResults).containsExactly(u1, ai1, result1, result2);

        // Add a new AiMessage, which triggers eviction and summarization (exceeds maxMessages=4)
        AiMessage ai2 = aiMessage("2 + 2 = 4, 3 + 3 = 6");
        chatMemory.add(ai2);

        List<ChatMessage> finalMsgs = chatMemory.messages();

        // The AiMessage (ai1) and its orphaned ToolExecutionResultMessages (result1, result2) are evicted.
        // The queue now contains a summary message followed by the latest AiMessage.
        assertThat(finalMsgs).hasSize(2);

        ChatMessage summary = finalMsgs.get(0);
        assertThat(summary)
                .usingRecursiveComparison()
                .ignoringFields("id", "timestamp", "createdAt")
                .isNotEqualTo(u1)
                .isNotEqualTo(ai1)
                .isNotEqualTo(result1)
                .isNotEqualTo(result2)
                .isNotEqualTo(ai2);

        assertThat(finalMsgs.get(1)).isEqualTo(ai2);
    }

    @ParameterizedTest
    @MethodSource("models")
    void
            should_evict_multiple_orphan_ToolExecutionResultMessages_when_evicting_AiMessage_with_ToolExecutionRequests_when_SystemMessage_is_present(
                    ChatModel chatModel) {

        // given: SummarizingChatMemory with maxMessages=5, summarizeThreshold=2 (>1)
        ChatMemory chatMemory = SummarizingChatMemory.builder()
                .maxMessages(5)
                .defaultGenerateSummaryFunction(chatModel)
                .maxMessagesToSummarize(2) // must be greater than 1
                .build();

        // Add a SystemMessage
        SystemMessage sys = systemMessage("Use calculator for math questions");
        chatMemory.add(sys);
        assertThat(chatMemory.messages()).containsExactly(sys);

        // Add a UserMessage
        UserMessage u1 = userMessage("How much is 2+2 and 3+3?");
        chatMemory.add(u1);
        assertThat(chatMemory.messages()).containsExactly(sys, u1);

        // Add an AiMessage containing two ToolExecutionRequests
        ToolExecutionRequest req1 = ToolExecutionRequest.builder()
                .id("1")
                .name("calculator")
                .arguments("{ \"a\": 2, \"b\": 2 }")
                .build();
        ToolExecutionRequest req2 = ToolExecutionRequest.builder()
                .id("2")
                .name("calculator")
                .arguments("{ \"a\": 3, \"b\": 3 }")
                .build();
        AiMessage ai1 = AiMessage.from(req1, req2);
        chatMemory.add(ai1);

        // Since maxMessages=5, the sequence [SystemMessage, UserMessage, AiMessage] does not trigger eviction
        assertThat(chatMemory.messages()).containsExactly(sys, u1, ai1);

        // Add the corresponding ToolExecutionResultMessages
        ToolExecutionResultMessage result1 = ToolExecutionResultMessage.from(req1, "4");
        ToolExecutionResultMessage result2 = ToolExecutionResultMessage.from(req2, "6");
        chatMemory.add(result1);
        chatMemory.add(result2);

        // At this point, the queue size is 5, reaching maxMessages, but the SystemMessage is not evicted
        List<ChatMessage> msgsAfterResults = chatMemory.messages();
        assertThat(msgsAfterResults).containsExactly(sys, u1, ai1, result1, result2);

        // Add a new AiMessage, which triggers eviction and summarization (exceeding maxMessages=5)
        AiMessage ai2 = aiMessage("2 + 2 = 4, 3 + 3 = 6");
        chatMemory.add(ai2);

        List<ChatMessage> finalMsgs = chatMemory.messages();

        // The AiMessage (ai1) and its orphaned ToolExecutionResultMessages should be evicted,
        // while the SystemMessage and the summary are retained
        assertThat(finalMsgs.get(0)).isEqualTo(sys);

        // The remaining messages (after the SystemMessage) should only contain summary messages and the latest
        // AiMessage;
        // no AiMessages with ToolExecutionRequests or ToolExecutionResultMessages should remain
        assertThat(finalMsgs.subList(1, finalMsgs.size()))
                .allMatch(msg -> !(msg instanceof AiMessage && ((AiMessage) msg).hasToolExecutionRequests())
                        && !(msg instanceof ToolExecutionResultMessage));

        // The last message in the queue is the newly added ai2
        assertThat(finalMsgs.get(finalMsgs.size() - 1)).isEqualTo(ai2);
    }

    @ParameterizedTest
    @MethodSource("models")
    void should_handle_dynamic_maxMessages_and_summarizeThreshold(ChatModel chatModel) {

        // Dynamic configuration: initial values maxMessages=4, summarizeThreshold=2 (>1)
        int[] dynamicMaxMessages = {4};
        int[] dynamicThreshold = {2};

        SummarizingChatMemory chatMemory = SummarizingChatMemory.builder()
                .defaultGenerateSummaryFunction(chatModel)
                .dynamicMaxMessages(id -> dynamicMaxMessages[0])
                .dynamicMaxMessagesToSummarize(id -> dynamicThreshold[0])
                .build();

        // Add a user message
        UserMessage u1 = userMessage("How much is 2+2 and 3+3?");
        chatMemory.add(u1);
        assertThat(chatMemory.messages()).containsExactly(u1);

        // Add an AiMessage containing two ToolExecutionRequests
        ToolExecutionRequest req1 = ToolExecutionRequest.builder()
                .id("1")
                .name("calculator")
                .arguments("{ \"a\": 2, \"b\": 2 }")
                .build();
        ToolExecutionRequest req2 = ToolExecutionRequest.builder()
                .id("2")
                .name("calculator")
                .arguments("{ \"a\": 3, \"b\": 3 }")
                .build();
        AiMessage ai1 = AiMessage.from(req1, req2);
        chatMemory.add(ai1);
        assertThat(chatMemory.messages()).contains(u1, ai1);

        // Add corresponding ToolExecutionResultMessages
        ToolExecutionResultMessage result1 = ToolExecutionResultMessage.from(req1, "4");
        ToolExecutionResultMessage result2 = ToolExecutionResultMessage.from(req2, "6");
        chatMemory.add(result1);
        chatMemory.add(result2);

        List<ChatMessage> msgsAfterResults = chatMemory.messages();
        assertThat(msgsAfterResults).containsExactly(u1, ai1, result1, result2);

        // Add a new AiMessage, which triggers eviction and summarization (exceeds maxMessages=4)
        AiMessage ai2 = aiMessage("2 + 2 = 4, 3 + 3 = 6");
        chatMemory.add(ai2);

        List<ChatMessage> finalMsgs = chatMemory.messages();
        // ai1 and its orphaned ToolExecutionResultMessages are evicted
        assertThat(finalMsgs).hasSize(2);
        ChatMessage summary = finalMsgs.get(0);
        assertThat(summary)
                .usingRecursiveComparison()
                .ignoringFields("id", "timestamp", "createdAt")
                .isNotEqualTo(u1)
                .isNotEqualTo(ai1)
                .isNotEqualTo(result1)
                .isNotEqualTo(result2)
                .isNotEqualTo(ai2);
        assertThat(finalMsgs.get(1)).isEqualTo(ai2);

        // ---------------- Dynamically update maxMessages and summarizeThreshold ----------------
        dynamicMaxMessages[0] = 5;
        dynamicThreshold[0] = 3;

        // Add a new AiMessage (ai3); summarization should not be triggered yet under the new threshold
        AiMessage ai3 = aiMessage("Extra calculation 5+5");
        chatMemory.add(ai3);

        List<ChatMessage> msgsAfterAi3 = chatMemory.messages();
        assertThat(msgsAfterAi3).contains(ai2, ai3); // Queue retains ai2 and ai3

        // Add another AiMessage (ai4); now exceeds the new maxMessages=5, triggering summarization
        AiMessage ai4 = aiMessage("Extra calculation 6+6");
        chatMemory.add(ai4);

        List<ChatMessage> msgsAfterAi4 = chatMemory.messages();
        assertThat(msgsAfterAi4).hasSizeGreaterThanOrEqualTo(2);
        assertThat(msgsAfterAi4.get(msgsAfterAi4.size() - 1)).isEqualTo(ai4);
    }

    static Stream<Arguments> models() {
        return Stream.of(Arguments.of(OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                //                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName("deepseek-chat")
                .logRequests(true)
                .logResponses(true)
                .build()));
    }
}
