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
}
