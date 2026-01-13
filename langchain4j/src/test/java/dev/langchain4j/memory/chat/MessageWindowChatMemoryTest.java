package dev.langchain4j.memory.chat;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import java.util.function.Function;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class MessageWindowChatMemoryTest implements WithAssertions {
    @Test
    void id() {
        {
            ChatMemory chatMemory =
                    MessageWindowChatMemory.builder().maxMessages(1).build();
            assertThat(chatMemory.id()).isEqualTo("default");
        }
        {
            ChatMemory chatMemory =
                    MessageWindowChatMemory.builder().id("abc").maxMessages(1).build();
            assertThat(chatMemory.id()).isEqualTo("abc");
        }
    }

    @Test
    void store_and_clear() {
        ChatMemory chatMemory = MessageWindowChatMemory.builder().maxMessages(2).build();

        chatMemory.add(userMessage("hello"));
        chatMemory.add(userMessage("world"));

        assertThat(chatMemory.messages()).containsExactly(userMessage("hello"), userMessage("world"));

        chatMemory.add(userMessage("banana"));

        assertThat(chatMemory.messages()).containsExactly(userMessage("world"), userMessage("banana"));

        chatMemory.clear();
        // idempotent
        chatMemory.clear();

        assertThat(chatMemory.messages()).isEmpty();
    }

    @Test
    void should_keep_specified_number_of_messages_in_chat_memory() {

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(3);

        UserMessage firstUserMessage = userMessage("hello");
        chatMemory.add(firstUserMessage);
        assertThat(chatMemory.messages()).hasSize(1).containsExactly(firstUserMessage);

        AiMessage firstAiMessage = aiMessage("hi");
        chatMemory.add(firstAiMessage);
        assertThat(chatMemory.messages()).hasSize(2).containsExactly(firstUserMessage, firstAiMessage);

        UserMessage secondUserMessage = userMessage("sup");
        chatMemory.add(secondUserMessage);
        assertThat(chatMemory.messages())
                .hasSize(3)
                .containsExactly(firstUserMessage, firstAiMessage, secondUserMessage);

        AiMessage secondAiMessage = aiMessage("not much");
        chatMemory.add(secondAiMessage);
        assertThat(chatMemory.messages())
                .hasSize(3)
                .containsExactly(
                        // firstUserMessage was evicted
                        firstAiMessage, secondUserMessage, secondAiMessage);
    }

    @Test
    void should_not_evict_system_message_from_chat_memory() {

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(3);

        SystemMessage systemMessage = systemMessage("You are a helpful assistant");
        chatMemory.add(systemMessage);

        UserMessage firstUserMessage = userMessage("Hello");
        chatMemory.add(firstUserMessage);

        AiMessage firstAiMessage = aiMessage("Hi, how can I help you?");
        chatMemory.add(firstAiMessage);

        assertThat(chatMemory.messages()).containsExactly(systemMessage, firstUserMessage, firstAiMessage);

        UserMessage secondUserMessage = userMessage("Tell me a joke");
        chatMemory.add(secondUserMessage);

        assertThat(chatMemory.messages())
                .containsExactly(
                        systemMessage,
                        // firstUserMessage was evicted
                        firstAiMessage,
                        secondUserMessage);

        AiMessage secondAiMessage =
                aiMessage("Why did the Java developer wear glasses? Because they didn't see sharp!");
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

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(3);

        SystemMessage firstSystemMessage = systemMessage("You are a helpful assistant");
        chatMemory.add(firstSystemMessage);

        UserMessage firstUserMessage = userMessage("Hello");
        chatMemory.add(firstUserMessage);

        AiMessage firstAiMessage = aiMessage("Hi, how can I help you?");
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

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(3);

        SystemMessage systemMessage = systemMessage("You are a helpful assistant");
        chatMemory.add(systemMessage);

        UserMessage userMessage = userMessage("Hello");
        chatMemory.add(userMessage);

        AiMessage aiMessage = aiMessage("Hi, how can I help you?");
        chatMemory.add(aiMessage);

        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage, aiMessage);

        chatMemory.add(systemMessage);

        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage, aiMessage);
    }

    @Test
    void should_evict_orphan_ToolExecutionResultMessage_when_evicting_AiMessage_with_ToolExecutionRequest() {

        // given
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(2);

        // when
        UserMessage userMessage = UserMessage.from("How much is 2+2?");
        chatMemory.add(userMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(userMessage);

        // when
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("calculator")
                .arguments("{ \"a\": 2, \"b\": 2 }")
                .build();
        AiMessage aiMessage = AiMessage.from(toolExecutionRequest);
        chatMemory.add(aiMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(userMessage, aiMessage);

        // when
        ToolExecutionResultMessage toolExecutionResultMessage =
                ToolExecutionResultMessage.from(toolExecutionRequest, "4");
        chatMemory.add(toolExecutionResultMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(aiMessage, toolExecutionResultMessage);

        // when new message is added and aiMessage (containing ToolExecutionRequest) has to be evicted
        AiMessage aiMessage2 = AiMessage.from("2 + 2 = 4");
        chatMemory.add(aiMessage2);

        // then orphan toolExecutionResultMessage is evicted together with aiMessage
        assertThat(chatMemory.messages()).containsExactly(aiMessage2);
    }

    @Test
    void
            should_evict_orphan_ToolExecutionResultMessage_when_evicting_AiMessage_with_ToolExecutionRequest_when_SystemMessage_is_present() {

        // given
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(3);

        // when
        SystemMessage systemMessage = SystemMessage.from("Use calculator for math questions");
        chatMemory.add(systemMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage);

        // when
        UserMessage userMessage = UserMessage.from("How much is 2+2?");
        chatMemory.add(userMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage);

        // when
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("calculator")
                .arguments("{ \"a\": 2, \"b\": 2 }")
                .build();
        AiMessage aiMessage = AiMessage.from(toolExecutionRequest);
        chatMemory.add(aiMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage, aiMessage);

        // when
        ToolExecutionResultMessage toolExecutionResultMessage =
                ToolExecutionResultMessage.from(toolExecutionRequest, "4");
        chatMemory.add(toolExecutionResultMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, aiMessage, toolExecutionResultMessage);

        // when aiMessage2 is added and aiMessage has to be evicted
        AiMessage aiMessage2 = AiMessage.from("2 + 2 = 4");
        chatMemory.add(aiMessage2);

        // then orphan toolExecutionResultMessage is evicted together with aiMessage
        assertThat(chatMemory.messages()).containsExactly(systemMessage, aiMessage2);
    }

    @Test
    void
            should_evict_orphan_ToolExecutionResultMessage_when_evicting_AiMessage_with_ToolExecutionRequest_when_SystemMessage_is_present_2() {

        // given chat memory with only 2 messages
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(2);

        // when
        SystemMessage systemMessage = SystemMessage.from("Use calculator for math questions");
        chatMemory.add(systemMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage);

        // when
        UserMessage userMessage = UserMessage.from("How much is 2+2?");
        chatMemory.add(userMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage);

        // when
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("1")
                .name("calculator")
                .arguments("{ \"a\": 2, \"b\": 2 }")
                .build();
        AiMessage aiMessage = AiMessage.from(toolExecutionRequest);
        chatMemory.add(aiMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, aiMessage);

        // when toolExecutionResultMessage is added and aiMessage has to be evicted
        ToolExecutionResultMessage toolExecutionResultMessage =
                ToolExecutionResultMessage.from(toolExecutionRequest, "4");
        chatMemory.add(toolExecutionResultMessage);

        // then orphan toolExecutionResultMessage is evicted together with aiMessage
        assertThat(chatMemory.messages()).containsExactly(systemMessage);
    }

    @Test
    void should_evict_multiple_orphan_ToolExecutionResultMessages_when_evicting_AiMessage_with_ToolExecutionRequests() {

        // given
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(3);

        // when
        UserMessage userMessage = UserMessage.from("How much is 2+2 and 3+3?");
        chatMemory.add(userMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(userMessage);

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
        chatMemory.add(aiMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(userMessage, aiMessage);

        // when
        ToolExecutionResultMessage toolExecutionResultMessage1 =
                ToolExecutionResultMessage.from(toolExecutionRequest1, "4");
        chatMemory.add(toolExecutionResultMessage1);

        // then
        assertThat(chatMemory.messages()).containsExactly(userMessage, aiMessage, toolExecutionResultMessage1);

        // when
        ToolExecutionResultMessage toolExecutionResultMessage2 =
                ToolExecutionResultMessage.from(toolExecutionRequest2, "6");
        chatMemory.add(toolExecutionResultMessage2);

        // then
        assertThat(chatMemory.messages())
                .containsExactly(aiMessage, toolExecutionResultMessage1, toolExecutionResultMessage2);

        // when aiMessage2 is added and aiMessage has to be evicted
        AiMessage aiMessage2 = AiMessage.from("2 + 2 = 4, 3 + 3 = 6");
        chatMemory.add(aiMessage2);

        // then orphan toolExecutionResultMessage1 and toolExecutionResultMessage2 are evicted together with aiMessage
        assertThat(chatMemory.messages()).containsExactly(aiMessage2);
    }

    @Test
    void
            should_evict_multiple_orphan_ToolExecutionResultMessages_when_evicting_AiMessage_with_ToolExecutionRequests_when_SystemMessage_is_present() {

        // given
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(4);

        // when
        SystemMessage systemMessage = SystemMessage.from("Use calculator for math questions");
        chatMemory.add(systemMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage);

        // when
        UserMessage userMessage = UserMessage.from("How much is 2+2 and 3+3?");
        chatMemory.add(userMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage);

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
        chatMemory.add(aiMessage);

        // then
        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage, aiMessage);

        // when
        ToolExecutionResultMessage toolExecutionResultMessage1 =
                ToolExecutionResultMessage.from(toolExecutionRequest1, "4");
        chatMemory.add(toolExecutionResultMessage1);

        // then
        assertThat(chatMemory.messages())
                .containsExactly(systemMessage, userMessage, aiMessage, toolExecutionResultMessage1);

        // when
        ToolExecutionResultMessage toolExecutionResultMessage2 =
                ToolExecutionResultMessage.from(toolExecutionRequest2, "6");
        chatMemory.add(toolExecutionResultMessage2);

        // then
        assertThat(chatMemory.messages())
                .containsExactly(systemMessage, aiMessage, toolExecutionResultMessage1, toolExecutionResultMessage2);

        // when aiMessage2 is added and aiMessage has to be evicted
        AiMessage aiMessage2 = AiMessage.from("2 + 2 = 4, 3 + 3 = 6");
        chatMemory.add(aiMessage2);

        // then orphan toolExecutionResultMessage1 and toolExecutionResultMessage2 are evicted together with aiMessage
        assertThat(chatMemory.messages()).containsExactly(systemMessage, aiMessage2);
    }

    @Test
    void dynamic_max_messages_behavior() {

        // Dynamic maxMessages function returns different limits based on memory ID
        Function<Object, Integer> dynamicMaxMessages = id -> {
            if ("short".equals(id)) return 2;
            if ("long".equals(id)) return 4;
            return 3;
        };

        var msgA = userMessage("a");
        var msgB = userMessage("b");
        var msgC = userMessage("c");
        var msg1 = userMessage("1");
        var msg2 = userMessage("2");
        var msg3 = userMessage("3");
        var msg4 = userMessage("4");
        var msg5 = userMessage("5");
        var msgX = userMessage("x");
        var msgY = userMessage("y");
        var msgZ = userMessage("z");
        var msgW = userMessage("w");

        // Create shortMemory with ID "short" (window size 2)
        MessageWindowChatMemory shortMemory = MessageWindowChatMemory.builder()
                .id("short")
                .dynamicMaxMessages(dynamicMaxMessages)
                .build();

        // Create longMemory with ID "long" (window size 4)
        MessageWindowChatMemory longMemory = MessageWindowChatMemory.builder()
                .id("long")
                .dynamicMaxMessages(dynamicMaxMessages)
                .build();

        // Add messages to shortMemory and exceed its limit
        shortMemory.add(msgA);
        shortMemory.add(msgB);
        shortMemory.add(msgC); // Exceeds maxMessages

        assertThat(shortMemory.messages()).containsExactly(msgB, msgC); // Oldest message is evicted

        // Test longMemory (window size 4)
        longMemory.add(msg1);
        longMemory.add(msg2);
        longMemory.add(msg3);
        longMemory.add(msg4);
        longMemory.add(msg5); // Exceeds maxMessages

        assertThat(longMemory.messages()).containsExactly(msg2, msg3, msg4, msg5);

        // Test default case (ID not matched, window size 3)
        MessageWindowChatMemory defaultMemory = MessageWindowChatMemory.builder()
                .id("other")
                .dynamicMaxMessages(dynamicMaxMessages)
                .build();

        defaultMemory.add(msgX);
        defaultMemory.add(msgY);
        defaultMemory.add(msgZ);
        defaultMemory.add(msgW); // Exceeds maxMessages

        assertThat(defaultMemory.messages()).containsExactly(msgY, msgZ, msgW);

        // Clear memory test
        shortMemory.clear();
        assertThat(shortMemory.messages()).isEmpty();
    }

    @Test
    void dynamic_max_messages_can_change_for_same_id() {

        // Array to hold the current dynamic max value
        int[] currentMax = {3};
        Function<Object, Integer> dynamicMaxMessages = id -> currentMax[0];

        // Create chat memory instance
        MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
                .id("same-id")
                .dynamicMaxMessages(dynamicMaxMessages)
                .build();

        var msgA = userMessage("A");
        var msgB = userMessage("B");
        var msgC = userMessage("C");
        var msgD = userMessage("D");
        var msgE = userMessage("E");

        memory.add(msgA);
        memory.add(msgB);
        memory.add(msgC);

        assertThat(memory.messages()).containsExactly(msgA, msgB, msgC);

        memory.add(msgD);

        assertThat(memory.messages()).containsExactly(msgB, msgC, msgD);

        // Increase maxMessages to 5 and add another message
        currentMax[0] = 5;

        memory.add(msgE);
        assertThat(memory.messages()).containsExactly(msgB, msgC, msgD, msgE);

        // Decrease maxMessages to 2, which should trigger eviction immediately
        currentMax[0] = 2;

        // Fetch messages list; excess messages are automatically evicted
        var msgsAfterShrink = memory.messages();
        assertThat(msgsAfterShrink).containsExactly(msgD, msgE); // Keep the most recent two messages
    }

    @Test
    void system_message_first_enabled() {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(5)
                .alwaysKeepSystemMessageFirst(true)
                .build();

        SystemMessage systemMessage = systemMessage("You are a helpful assistant");
        chatMemory.add(systemMessage);

        UserMessage userMessage = userMessage("Hello");
        chatMemory.add(userMessage);

        AiMessage aiMessage = aiMessage("Hi, how can I help?");
        chatMemory.add(aiMessage);

        // System message should be at the beginning
        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage, aiMessage);

        chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(5)
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
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(5)
                .alwaysKeepSystemMessageFirst(false)
                .build();

        SystemMessage systemMessage = systemMessage("You are a helpful assistant");
        chatMemory.add(systemMessage);

        UserMessage userMessage = userMessage("Hello");
        chatMemory.add(userMessage);

        AiMessage aiMessage = aiMessage("Hi, how can I help?");
        chatMemory.add(aiMessage);

        // System message should be at the beginning
        assertThat(chatMemory.messages()).containsExactly(systemMessage, userMessage, aiMessage);

        chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(5)
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
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(3)
                .alwaysKeepSystemMessageFirst(true)
                .build();

        SystemMessage systemMessage = systemMessage("You are a helpful assistant");
        chatMemory.add(systemMessage);

        UserMessage msg1 = userMessage("Message 1");
        chatMemory.add(msg1);

        UserMessage msg2 = userMessage("Message 2");
        chatMemory.add(msg2);

        // At capacity: systemMessage, msg1, msg2
        assertThat(chatMemory.messages()).containsExactly(systemMessage, msg1, msg2);

        UserMessage msg3 = userMessage("Message 3");
        chatMemory.add(msg3);

        // msg1 should be evicted, systemMessage should remain at the beginning
        assertThat(chatMemory.messages()).containsExactly(systemMessage, msg2, msg3);
    }
}
