package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class JacksonStateJsonCodecTest {

    private final Json.JsonCodec codec = StateJson.codec(new TypeAllowlist());

    private Object roundTrip(Object value) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("value", value);
        Map<?, ?> restored = codec.fromJson(codec.toJson(state), LinkedHashMap.class);
        return restored.get("value");
    }

    @Test
    void should_resolve_to_the_jackson2_codec() {
        assertThat(codec).isInstanceOf(JacksonStateJsonCodec.class);
    }

    @ParameterizedTest
    @MethodSource
    void should_read_back_a_chat_message_stored_as_a_state_value(ChatMessage message) {
        assertThat(roundTrip(message)).isEqualTo(message);
    }

    static Stream<ChatMessage> should_read_back_a_chat_message_stored_as_a_state_value() {
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .id("12345")
                .name("weather")
                .arguments("{\"city\": \"Munich\"}")
                .build();
        return Stream.of(
                SystemMessage.from("hello"),
                SystemMessage.builder()
                        .text("hello")
                        .attributes(Map.of("prompt_cache_breakpoint", "explicit"))
                        .build(),
                UserMessage.from("hello"),
                UserMessage.from("Klaus", "hello"),
                UserMessage.from(TextContent.from("hello"), ImageContent.from("http://image.url")),
                UserMessage.builder()
                        .addContent(TextContent.from("hello"))
                        .attributes(Map.of("name", "Klaus"))
                        .build(),
                AiMessage.from("hello"),
                AiMessage.builder()
                        .text("test-text")
                        .thinking("test-thinking")
                        .toolExecutionRequests(List.of(toolExecutionRequest))
                        .build(),
                ToolExecutionResultMessage.from(toolExecutionRequest, "sunny"),
                ToolExecutionResultMessage.builder()
                        .id("12345")
                        .toolName("weather")
                        .text("error occurred")
                        .isError(true)
                        .build(),
                CustomMessage.from(Map.of("key", "value")));
    }

    @ParameterizedTest
    @MethodSource
    void should_read_back_a_content_stored_as_a_state_value(Content content) {
        assertThat(roundTrip(content)).isEqualTo(content);
    }

    static Stream<Content> should_read_back_a_content_stored_as_a_state_value() {
        return Stream.of(
                TextContent.from("hello"),
                ImageContent.from("http://image.url"),
                ImageContent.from("aGVsbG8=", "image/png"),
                AudioContent.from("bXAz", "audio/mp3"),
                VideoContent.from("bXA0", "video/mp4"),
                PdfFileContent.from("cGRm", "application/pdf"));
    }

    @Test
    void should_read_back_chat_messages_inside_a_list_stored_as_a_state_value() {
        List<ChatMessage> messages = new ArrayList<>(List.of(UserMessage.from("hello"), AiMessage.from("hi")));

        assertThat(roundTrip(messages)).isEqualTo(messages);
    }

    @Test
    void should_read_back_chat_messages_inside_a_map_stored_as_a_state_value() {
        Map<String, ChatMessage> messages = new LinkedHashMap<>();
        messages.put("question", UserMessage.from("hello"));
        messages.put("answer", AiMessage.from("hi"));

        assertThat(roundTrip(messages)).isEqualTo(messages);
    }

    @Test
    void should_still_write_the_type_of_a_chat_message_stored_as_a_state_value() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("message", UserMessage.from("hello"));

        assertThat(codec.toJson(state))
                .isEqualTo("{\"message\":[\"dev.langchain4j.data.message.UserMessage\","
                        + "{\"contents\":[\"java.util.Collections$UnmodifiableRandomAccessList\","
                        + "[{\"text\":\"hello\",\"type\":\"TEXT\"}]],\"type\":\"USER\"}]}");
    }

    @Test
    void should_still_reject_an_unknown_property_of_a_chat_message_stored_as_a_state_value() {
        String json = "{\"message\":[\"dev.langchain4j.data.message.UserMessage\","
                + "{\"contents\":[\"java.util.ArrayList\",[{\"text\":\"hello\",\"type\":\"TEXT\"}]],"
                + "\"type\":\"USER\",\"unknown\":\"value\"}]}";

        assertThatThrownBy(() -> codec.fromJson(json, LinkedHashMap.class))
                .rootCause()
                .isInstanceOf(UnrecognizedPropertyException.class)
                .hasMessageStartingWith("Unrecognized field \"unknown\"");
    }
}
