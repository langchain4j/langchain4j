package dev.langchain4j.data.message;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messageFromJson;
import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messageToJson;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageSerializerTest {

    @ParameterizedTest
    @MethodSource
    void should_serialize_and_deserialize_chat_message(ChatMessage message, String expectedJson) {

        String json = messageToJson(message);
        assertThat(json).isEqualToIgnoringWhitespace(expectedJson);

        ChatMessage deserializedMessage = messageFromJson(json);
        assertThat(deserializedMessage).isEqualTo(message);
    }

    static Stream<Arguments> should_serialize_and_deserialize_chat_message() {
        return Stream.of(
                Arguments.of(
                        SystemMessage.from("hello"),
                        "{\"text\":\"hello\",\"type\":\"SYSTEM\"}"
                ),
                Arguments.of(
                        UserMessage.from("hello"),
                        "{\"contents\":[{\"text\":\"hello\",\"type\":\"TEXT\"}],\"type\":\"USER\"}"
                ),
                Arguments.of(
                        UserMessage.from("Klaus", "hello"),
                        "{\"name\":\"Klaus\",\"contents\":[{\"text\":\"hello\",\"type\":\"TEXT\"}],\"type\":\"USER\"}"
                ),
                Arguments.of(
                        UserMessage.from(ImageContent.from("http://image.url")),
                        "{\"contents\":[{\"image\":{\"url\":\"http://image.url\"},\"detailLevel\":\"LOW\",\"type\":\"IMAGE\"}],\"type\":\"USER\"}"
                ),
                Arguments.of(
                        UserMessage.from(ImageContent.from("aGVsbG8=", "image/png")),
                        "{\"contents\":[{\"image\":{\"base64Data\":\"aGVsbG8\\u003d\",\"mimeType\":\"image/png\"},\"detailLevel\":\"LOW\",\"type\":\"IMAGE\"}],\"type\":\"USER\"}"
                ),
                Arguments.of(
                        UserMessage.from(AudioContent.from("bXAz", "audio/mp3")),
                        "{\"contents\":[{\"audio\":{\"base64Data\":\"bXAz\",\"mimeType\":\"audio/mp3\"},\"type\":\"AUDIO\"}],\"type\":\"USER\"}"
                ),
                Arguments.of(
                        UserMessage.from(VideoContent.from("bXA0", "video/mp4")),
                        "{\"contents\":[{\"video\":{\"base64Data\":\"bXA0\",\"mimeType\":\"video/mp4\"},\"type\":\"VIDEO\"}],\"type\":\"USER\"}"
                ),
                Arguments.of(
                        UserMessage.from(PdfFileContent.from("cGRm", "application/pdf")),
                        "{\"contents\":[{\"pdfFile\":{\"base64Data\":\"cGRm\"},\"type\":\"PDF\"}],\"type\":\"USER\"}"
                ),
                Arguments.of(
                        AiMessage.from("hello"),
                        "{\"text\":\"hello\",\"type\":\"AI\"}"
                ),
                Arguments.of(
                        AiMessage.from(ToolExecutionRequest.builder()
                                .name("weather")
                                .arguments("{\"city\": \"Munich\"}")
                                .build()),
                        "{\"toolExecutionRequests\":[{\"name\":\"weather\",\"arguments\":\"{\\\"city\\\": \\\"Munich\\\"}\"}],\"type\":\"AI\"}"
                ),
                Arguments.of(
                        ToolExecutionResultMessage.from("12345", "weather", "sunny"),
                        "{\"id\":\"12345\",\"toolName\":\"weather\",\"text\":\"sunny\",\"type\":\"TOOL_EXECUTION_RESULT\"}"
                )
        );
    }

    @Test
    void should_deserialize_user_message_in_old_schema() {

        String json = "{\"text\":\"hello\",\"type\":\"USER\"}";

        ChatMessage deserializedMessage = messageFromJson(json);

        assertThat(deserializedMessage).isEqualTo(UserMessage.from("hello"));
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
        assertThat(json).isEqualTo("[{\"contents\":[{\"text\":\"hello\",\"type\":\"TEXT\"}],\"type\":\"USER\"}]");

        List<ChatMessage> deserializedMessages = messagesFromJson(json);
        assertThat(deserializedMessages).isEqualTo(messages);
    }

    @Test
    void should_serialize_and_deserialize_list_with_one_message_in_old_schema() {

        String json = "[{\"text\":\"hello\",\"type\":\"USER\"}]";

        List<ChatMessage> deserializedMessages = messagesFromJson(json);

        assertThat(deserializedMessages).containsExactly(UserMessage.from("hello"));
    }
}