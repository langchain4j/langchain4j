package dev.langchain4j.rag.query;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class MetadataTest {

    @Test
    void create() {

        // given
        UserMessage userMessage = UserMessage.from("user message");
        int chatMemoryId = 42;
        List<ChatMessage> chatMemory =
                asList(UserMessage.from("Hello"), AiMessage.from("Hi, how can I help you today?"));

        // when
        Metadata metadata = Metadata.from(userMessage, chatMemoryId, chatMemory);

        // then
        assertThat(metadata.chatMessage()).isSameAs(userMessage);

        assertThat(metadata.chatMemoryId()).isSameAs(chatMemoryId);

        assertThat(metadata.chatMemory()).isNotSameAs(chatMemory).isEqualTo(chatMemory);
    }

    @Test
    void equals_hash_code() {

        // given
        Metadata metadata1 = Metadata.from(
                UserMessage.from("user message"),
                42,
                asList(UserMessage.from("Hello"), AiMessage.from("Hi, how can I help you today?")));

        Metadata metadata2 = Metadata.from(
                UserMessage.from("another user message"),
                666,
                asList(UserMessage.from("Bye"), AiMessage.from("Bye-bye")));

        Metadata metadata3 = Metadata.from(
                UserMessage.from("user message"),
                42,
                asList(UserMessage.from("Hello"), AiMessage.from("Hi, how can I help you today?")));

        // then
        assertThat(metadata1).isNotEqualTo(metadata2).doesNotHaveSameHashCodeAs(metadata2);

        assertThat(metadata1).isEqualTo(metadata3).hasSameHashCodeAs(metadata3);
    }

    @Test
    void to_string() {

        // given
        Metadata metadata = Metadata.from(
                UserMessage.from("user message"),
                42,
                asList(UserMessage.from("Hello"), AiMessage.from("Hi, how can I help you today?")));

        // when
        String toString = metadata.toString();

        // then
        assertThat(toString)
                .isEqualToIgnoringWhitespace("""
                        Metadata { chatMessage = UserMessage { name = null, contents = [TextContent { text = "user message" }], attributes = {} }, chatMemory = [UserMessage { name = null, contents = [TextContent { text = "Hello" }], attributes = {} }, AiMessage { text = "Hi, how can I help you today?", thinking = null, toolExecutionRequests = [], attributes = {} }], invocationContext = DefaultInvocationContext{invocationId=null, interfaceName='null', methodName='null', methodArguments=[], chatMemoryId=42, invocationParameters=null, managedParameters=null, timestamp=null} }
                        """);
    }

    @Test
    void create_with_empty_chat_memory() {
        // given
        UserMessage userMessage = UserMessage.from("user message");
        int chatMemoryId = 1;
        List<ChatMessage> chatMemory = asList();

        // when
        Metadata metadata = Metadata.from(userMessage, chatMemoryId, chatMemory);

        // then
        assertThat(metadata.chatMessage()).isSameAs(userMessage);
        assertThat(metadata.chatMemoryId()).isSameAs(chatMemoryId);
        assertThat(metadata.chatMemory()).isEmpty();
    }

    @Test
    void create_with_different_message_types() {
        // given
        AiMessage aiMessage = AiMessage.from("ai response");
        int chatMemoryId = 1;
        List<ChatMessage> chatMemory =
                asList(UserMessage.from("Question 1"), AiMessage.from("Answer 1"), UserMessage.from("Question 2"));

        // when
        Metadata metadata = Metadata.from(aiMessage, chatMemoryId, chatMemory);

        // then
        assertThat(metadata.chatMessage()).isSameAs(aiMessage);
        assertThat(metadata.chatMemoryId()).isSameAs(chatMemoryId);
        assertThat(metadata.chatMemory()).hasSize(3);
    }

    @Test
    void create_with_zero_chat_memory_id() {
        // given
        UserMessage userMessage = UserMessage.from("test message");
        int chatMemoryId = 0;
        List<ChatMessage> chatMemory = asList(UserMessage.from("Hello"));

        // when
        Metadata metadata = Metadata.from(userMessage, chatMemoryId, chatMemory);

        // then
        assertThat(metadata.chatMemoryId()).isEqualTo(0);
    }

    @Test
    void equals_with_null_chat_memory() {
        // given
        Metadata metadata1 = Metadata.from(UserMessage.from("message"), 1, null);

        Metadata metadata2 = Metadata.from(UserMessage.from("message"), 1, null);

        // then
        assertThat(metadata1).isEqualTo(metadata2).hasSameHashCodeAs(metadata2);
    }

    @Test
    void equals_with_different_chat_memory_ids() {
        // given
        List<ChatMessage> chatMemory = asList(UserMessage.from("Hello"));

        Metadata metadata1 = Metadata.from(UserMessage.from("message"), 1, chatMemory);

        Metadata metadata2 = Metadata.from(UserMessage.from("message"), 2, chatMemory);

        // then
        assertThat(metadata1).isNotEqualTo(metadata2).doesNotHaveSameHashCodeAs(metadata2);
    }

    @Test
    void equals_with_different_chat_messages() {
        // given
        List<ChatMessage> chatMemory = asList(UserMessage.from("Hello"));

        Metadata metadata1 = Metadata.from(UserMessage.from("message1"), 1, chatMemory);

        Metadata metadata2 = Metadata.from(UserMessage.from("message2"), 1, chatMemory);

        // then
        assertThat(metadata1).isNotEqualTo(metadata2).doesNotHaveSameHashCodeAs(metadata2);
    }

    @Test
    void chat_memory_immutability() {
        // given
        List<ChatMessage> originalChatMemory = asList(UserMessage.from("Hello"), AiMessage.from("Hi"));

        Metadata metadata = Metadata.from(UserMessage.from("message"), 1, originalChatMemory);

        // when
        List<ChatMessage> retrievedChatMemory = metadata.chatMemory();

        // then - verify defensive copy was made
        assertThat(retrievedChatMemory).isNotSameAs(originalChatMemory);
        assertThat(retrievedChatMemory).isEqualTo(originalChatMemory);
    }

    @Test
    void should_handle_null_chat_message() {
        assertThatThrownBy(() -> Metadata.from(null, 1, asList(UserMessage.from("user message"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chatMessage cannot be null");
    }
}
