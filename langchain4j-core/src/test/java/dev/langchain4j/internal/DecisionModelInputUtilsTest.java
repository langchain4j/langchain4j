package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.CustomMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DecisionModelInputUtilsTest {

    static final ToolExecutionRequest TOOL_CALL = ToolExecutionRequest.builder()
            .id("1")
            .name("weather")
            .arguments("{\"city\":\"Berlin\"}")
            .build();

    @Test
    void window_should_keep_system_messages_the_last_messages_and_the_last_user_message() {

        List<ChatMessage> window = DecisionModelInputUtils.window(
                List.of(
                        SystemMessage.from("You are a helpful assistant"),
                        UserMessage.from("Hi!"),
                        AiMessage.from("Hello!"),
                        UserMessage.from("Weather in Berlin?"),
                        AiMessage.from(TOOL_CALL),
                        ToolExecutionResultMessage.from(TOOL_CALL, "{\"temperature\": 20}"),
                        AiMessage.from(TOOL_CALL),
                        ToolExecutionResultMessage.from(TOOL_CALL, "{\"temperature\": 21}"),
                        AiMessage.builder().thinking("The user wants the weather.").build(),
                        CustomMessage.from(Map.of("key", "value"))),
                message -> true,
                2,
                100);

        assertThat(window)
                .containsExactly(
                        SystemMessage.from("You are a helpful assistant"),
                        UserMessage.from("Weather in Berlin?"),
                        AiMessage.from(TOOL_CALL),
                        ToolExecutionResultMessage.from(TOOL_CALL, "{\"temperature\": 21}"));
    }

    @Test
    void window_should_leave_out_tool_results_at_its_start_without_the_ai_message_that_requested_them() {

        ToolExecutionRequest a = ToolExecutionRequest.builder().id("a").name("weather").arguments("{}").build();
        ToolExecutionRequest b = ToolExecutionRequest.builder().id("b").name("news").arguments("{}").build();
        ToolExecutionRequest c = ToolExecutionRequest.builder().id("c").name("traffic").arguments("{}").build();

        List<ChatMessage> window = DecisionModelInputUtils.window(
                List.of(
                        UserMessage.from("What's up in Berlin?"),
                        AiMessage.from(a, b, c),
                        ToolExecutionResultMessage.from(a, "sunny"),
                        ToolExecutionResultMessage.from(b, "nothing"),
                        ToolExecutionResultMessage.from(c, "jam on A100"),
                        AiMessage.from("Sunny, no news, and a jam on the A100.")),
                message -> true,
                3,
                100);

        assertThat(window)
                .containsExactly(
                        UserMessage.from("What's up in Berlin?"),
                        AiMessage.from("Sunny, no news, and a jam on the A100."));
    }

    @Test
    void window_should_keep_tool_results_that_fit_with_the_ai_message_that_requested_them() {

        ToolExecutionRequest a = ToolExecutionRequest.builder().id("a").name("weather").arguments("{}").build();
        ToolExecutionRequest b = ToolExecutionRequest.builder().id("b").name("news").arguments("{}").build();

        List<ChatMessage> window = DecisionModelInputUtils.window(
                List.of(
                        UserMessage.from("What's up in Berlin?"),
                        AiMessage.from(a, b),
                        ToolExecutionResultMessage.from(a, "sunny"),
                        ToolExecutionResultMessage.from(b, "nothing")),
                message -> true,
                3,
                100);

        assertThat(window)
                .containsExactly(
                        UserMessage.from("What's up in Berlin?"),
                        AiMessage.from(a, b),
                        ToolExecutionResultMessage.from(a, "sunny"),
                        ToolExecutionResultMessage.from(b, "nothing"));
    }

    @Test
    void window_should_leave_out_tool_results_at_its_start_when_the_filter_drops_the_ai_messages() {

        ToolExecutionRequest a = ToolExecutionRequest.builder().id("a").name("weather").arguments("{}").build();
        ToolExecutionRequest b = ToolExecutionRequest.builder().id("b").name("news").arguments("{}").build();

        List<ChatMessage> window = DecisionModelInputUtils.window(
                List.of(
                        UserMessage.from("What's up in Berlin?"),
                        AiMessage.from(a),
                        ToolExecutionResultMessage.from(a, "sunny"),
                        AiMessage.from(b),
                        ToolExecutionResultMessage.from(b, "nothing")),
                message -> !(message instanceof AiMessage),
                2,
                100);

        assertThat(window).containsExactly(UserMessage.from("What's up in Berlin?"));
    }

    @Test
    void window_with_no_messages_should_keep_only_system_messages() {

        assertThat(DecisionModelInputUtils.window(
                        List.of(SystemMessage.from("You are a helpful assistant"), UserMessage.from("Hi!")),
                        message -> true,
                        0,
                        100))
                .containsExactly(SystemMessage.from("You are a helpful assistant"));
    }

    @Test
    void window_should_apply_the_filter_and_mark_attachments() {

        List<ChatMessage> window = DecisionModelInputUtils.window(
                List.of(
                        SystemMessage.from("You are a helpful assistant"),
                        UserMessage.from("Anna", TextContent.from("What is this?"), ImageContent.from("https://example.com/cat.png")),
                        AiMessage.from("A cat.")),
                message -> !(message instanceof SystemMessage),
                10,
                100);

        assertThat(window).containsExactly(UserMessage.from("Anna", "What is this?\n[attached image]"), AiMessage.from("A cat."));
    }

    @Test
    void window_should_shorten_long_messages_but_not_system_messages() {

        String longText = "a".repeat(200) + "b".repeat(100);
        List<ChatMessage> window = DecisionModelInputUtils.window(
                List.of(
                        SystemMessage.from(longText),
                        UserMessage.from(longText),
                        AiMessage.from(TOOL_CALL),
                        ToolExecutionResultMessage.from(TOOL_CALL, longText),
                        AiMessage.from(longText)),
                message -> true,
                10,
                30);

        String shortened = "a".repeat(20) + "\n[270 characters were omitted here to save space; the assistant saw the full text.]\n"
                + "b".repeat(10);
        assertThat(window)
                .containsExactly(
                        SystemMessage.from(longText),
                        UserMessage.from(shortened),
                        AiMessage.from(TOOL_CALL),
                        ToolExecutionResultMessage.from(TOOL_CALL, shortened),
                        AiMessage.from(shortened));
    }

    @Test
    void window_should_shorten_tool_call_arguments() {

        ToolExecutionRequest writeFile = ToolExecutionRequest.builder()
                .id("1")
                .name("writeFile")
                .arguments("a".repeat(200) + "b".repeat(100))
                .build();

        List<ChatMessage> window = DecisionModelInputUtils.window(List.of(AiMessage.from(writeFile)), message -> true, 10, 30);

        assertThat(window)
                .containsExactly(AiMessage.from(ToolExecutionRequest.builder()
                        .id("1")
                        .name("writeFile")
                        .arguments("a".repeat(20)
                                + "\n[270 characters were omitted here to save space; the assistant saw the full text.]\n"
                                + "b".repeat(10))
                        .build()));
    }

    @Test
    void transcript_should_leave_out_missing_ids_and_arguments() {

        ToolExecutionRequest withoutId = ToolExecutionRequest.builder().name("now").build();

        assertThat(DecisionModelInputUtils.transcript(
                                List.of(AiMessage.from(withoutId), ToolExecutionResultMessage.from(null, "now", "12:00")),
                                "The test model")
                        .messages())
                .containsExactly(
                        Map.of("role", "assistant", "tool_calls", List.of(Map.of("name", "now"))),
                        Map.of("role", "tool", "name", "now", "text", "12:00"));
    }

    @Test
    void truncate_should_keep_short_texts_and_not_split_surrogate_pairs() {

        assertThat(DecisionModelInputUtils.truncate("short", 10)).isEqualTo("short");
        assertThat(DecisionModelInputUtils.truncate("12345", 5)).isEqualTo("12345");
        // a text that the note would make longer is kept as it is
        assertThat(DecisionModelInputUtils.truncate("a".repeat(50), 10)).isEqualTo("a".repeat(50));
        assertThat(DecisionModelInputUtils.truncate(null, 10)).isNull();
        // an unpaired surrogate would show up as a code point in the surrogate range
        for (int maxLength : new int[] {5, 7}) {
            String truncated = DecisionModelInputUtils.truncate("😀".repeat(100), maxLength);
            assertThat(truncated.codePoints().noneMatch(codePoint -> codePoint >= 0xD800 && codePoint <= 0xDFFF))
                    .isTrue();
        }
    }

    @Test
    void transcript_should_describe_every_message_and_refer_to_images_by_position() {

        ImageContent photo = ImageContent.from("iVBORw0KGgo=", "image/png");
        ToolExecutionResultMessage failed = ToolExecutionResultMessage.builder()
                .id("1")
                .toolName("weather")
                .contents(TextContent.from("Service unavailable"), photo)
                .isError(true)
                .build();

        DecisionModelInputUtils.Transcript transcript = DecisionModelInputUtils.transcript(
                List.of(
                        SystemMessage.from("You are a helpful assistant"),
                        UserMessage.from("Anna", TextContent.from("Weather here?"), photo),
                        AiMessage.builder()
                                .text("Let me check.")
                                .thinking("The user wants the weather.")
                                .toolExecutionRequests(List.of(TOOL_CALL))
                                .build(),
                        failed,
                        AiMessage.builder().thinking("Nothing to say.").build()),
                "The test model");

        Map<String, Object> toolCall = new LinkedHashMap<>();
        toolCall.put("id", "1");
        toolCall.put("name", "weather");
        toolCall.put("arguments", "{\"city\":\"Berlin\"}");
        Map<String, Object> toolResult = new LinkedHashMap<>();
        toolResult.put("role", "tool");
        toolResult.put("tool_call_id", "1");
        toolResult.put("name", "weather");
        toolResult.put("text", "Service unavailable\n[image 2]");
        toolResult.put("is_error", true);
        assertThat(transcript.messages())
                .containsExactly(
                        Map.of("role", "system", "text", "You are a helpful assistant"),
                        Map.of("role", "user", "name", "Anna", "text", "Weather here?\n[image 1]"),
                        Map.of("role", "assistant", "text", "Let me check.", "tool_calls", List.of(toolCall)),
                        toolResult);
        assertThat(transcript.images()).containsExactly(photo, photo);
    }

    @Test
    void transcript_should_reject_unsupported_messages_and_contents_and_empty_conversations() {

        assertThatThrownBy(() -> DecisionModelInputUtils.transcript(
                        List.of(CustomMessage.from(Map.of("key", "value"))), "The test model"))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("The test model does not support CustomMessage in a conversation. Supported messages: "
                        + "SystemMessage, UserMessage, AiMessage and ToolExecutionResultMessage");
        assertThatThrownBy(() -> DecisionModelInputUtils.transcript(
                        List.of(UserMessage.from(AudioContent.from("YXVkaW8=", "audio/wav"))), "The test model"))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessage("The test model supports only text and images in a conversation, but it contains AUDIO");
        assertThatThrownBy(() -> DecisionModelInputUtils.transcript(
                        List.of(AiMessage.builder().thinking("...").build()), "The test model"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The conversation has nothing to send: AiMessages without text and tool execution "
                        + "requests are skipped");
    }
}
