package dev.langchain4j.data.message;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.ChatMessageDeserializer.messageFromJson;
import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.ToolExecutionResultMessage.toolExecutionResultMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageSerializerTest {

    @Test
    void should_serialize_and_deserialize_chat_message() {

        UserMessage message = userMessage("hello");

        String json = ChatMessageSerializer.messageToJson(message);
        ChatMessage deserializedMessage = messageFromJson(json);

        assertThat(deserializedMessage).isEqualTo(message);
    }

    @Test
    void should_serialize_and_deserialize_empty_list() {

        List<ChatMessage> messages = emptyList();

        String json = ChatMessageSerializer.messagesToJson(messages);
        List<ChatMessage> deserializedMessages = messagesFromJson(json);

        assertThat(deserializedMessages).isEmpty();
    }

    @Test
    void should_deserialize_null_as_empty_list() {
        assertThat(messagesFromJson(null)).isEmpty();
    }

    @Test
    void should_serialize_and_deserialize_list_with_one_message() {

        List<ChatMessage> messages = singletonList(userMessage("hello"));

        String json = ChatMessageSerializer.messagesToJson(messages);
        assertThat(json).isEqualTo("[{\"text\":\"hello\",\"type\":\"USER\"}]");

        List<ChatMessage> deserializedMessages = messagesFromJson(json);
        assertThat(deserializedMessages).isEqualTo(messages);
    }

    @Test
    void should_serialize_and_deserialize_list_with_all_types_of_messages() {

        List<ChatMessage> messages = asList(
                systemMessage("Hello from system"),
                userMessage("Hello from user"),
                userMessage("Klaus", "Hello from Klaus"),
                aiMessage("Hello from AI"),
                aiMessage(ToolExecutionRequest.builder()
                        .name("calculator")
                        .arguments("{}")
                        .build()),
                toolExecutionResultMessage("12345", "calculator", "4")
        );

        String json = ChatMessageSerializer.messagesToJson(messages);
        assertThat(json).isEqualTo("[" +
                "{\"text\":\"Hello from system\",\"type\":\"SYSTEM\"}," +
                "{\"text\":\"Hello from user\",\"type\":\"USER\"}," +
                "{\"name\":\"Klaus\",\"text\":\"Hello from Klaus\",\"type\":\"USER\"}," +
                "{\"text\":\"Hello from AI\",\"type\":\"AI\"}," +
                "{\"toolExecutionRequests\":[{\"name\":\"calculator\",\"arguments\":\"{}\"}],\"type\":\"AI\"}," +
                "{\"id\":\"12345\",\"toolName\":\"calculator\",\"text\":\"4\",\"type\":\"TOOL_EXECUTION_RESULT\"}" +
                "]");

        List<ChatMessage> deserializedMessages = messagesFromJson(json);
        assertThat(deserializedMessages).isEqualTo(messages);
    }
}