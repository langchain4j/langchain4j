package dev.langchain4j.rag.query;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class MetadataTest {

    @Test
    void test_create() {

        // given
        UserMessage userMessage = UserMessage.from("user message");
        int chatMemoryId = 42;
        List<ChatMessage> chatMemory = asList(
                UserMessage.from("Hello"),
                AiMessage.from("Hi, how can I help you today?")
        );

        // when
        Metadata metadata = Metadata.from(userMessage, chatMemoryId, chatMemory);

        // then
        assertThat(metadata.userMessage()).isSameAs(userMessage);

        assertThat(metadata.chatMemoryId()).isSameAs(chatMemoryId);

        assertThat(metadata.chatMemory())
                .isNotSameAs(chatMemory)
                .isEqualTo(chatMemory);
    }

    @Test
    void test_equals_hashCode() {

        // given
        Metadata metadata1 = Metadata.from(
                UserMessage.from("user message"),
                42,
                asList(
                        UserMessage.from("Hello"),
                        AiMessage.from("Hi, how can I help you today?")
                )
        );

        Metadata metadata2 = Metadata.from(
                UserMessage.from("another user message"),
                666,
                asList(
                        UserMessage.from("Bye"),
                        AiMessage.from("Bye-bye")
                )
        );

        Metadata metadata3 = Metadata.from(
                UserMessage.from("user message"),
                42,
                asList(
                        UserMessage.from("Hello"),
                        AiMessage.from("Hi, how can I help you today?")
                )
        );

        // then
        assertThat(metadata1)
                .isNotEqualTo(metadata2)
                .doesNotHaveSameHashCodeAs(metadata2);

        assertThat(metadata1)
                .isEqualTo(metadata3)
                .hasSameHashCodeAs(metadata3);
    }

    @Test
    void test_toString() {

        // given
        Metadata metadata = Metadata.from(
                UserMessage.from("user message"),
                42,
                asList(
                        UserMessage.from("Hello"),
                        AiMessage.from("Hi, how can I help you today?")
                )
        );

        // when
        String toString = metadata.toString();

        // then
        assertThat(toString).isEqualTo("Metadata { " +
                "userMessage = UserMessage { name = null contents = [TextContent { text = \"user message\" }] }, " +
                "chatMemoryId = 42, " +
                "chatMemory = [UserMessage { name = null contents = [TextContent { text = \"Hello\" }] }, AiMessage { text = \"Hi, how can I help you today?\" toolExecutionRequests = null }] " +
                "}");
    }
}