package dev.langchain4j.model.google.genai;

import static dev.langchain4j.model.output.FinishReason.CONTENT_FILTER;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.OTHER;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.genai.types.Blob;
import com.google.genai.types.Candidate;
import com.google.genai.types.CodeExecutionResult;
import com.google.genai.types.Content;
import com.google.genai.types.ExecutableCode;
import com.google.genai.types.FinishReason;
import com.google.genai.types.FinishReason.Known;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import com.google.genai.types.Transcription;
import com.google.genai.types.WordInfo;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoogleGenAiContentMapperTest {

    @Test
    void should_return_null_when_no_system_messages() {
        List<ChatMessage> messages = List.of(UserMessage.from("Hello"));

        Content result = GoogleGenAiContentMapper.toSystemInstruction(messages);

        assertThat(result).isNull();
    }

    @Test
    void should_extract_system_instruction() {
        List<ChatMessage> messages = List.of(SystemMessage.from("You are helpful"), UserMessage.from("Hello"));

        Content result = GoogleGenAiContentMapper.toSystemInstruction(messages);

        assertThat(result).isNotNull();
        assertThat(result.role().get()).isEqualTo("system");
        assertThat(result.parts().get().get(0).text().get()).isEqualTo("You are helpful");
    }

    @Test
    void should_join_multiple_system_messages() {
        List<ChatMessage> messages = List.of(
                SystemMessage.from("First instruction"),
                SystemMessage.from("Second instruction"),
                UserMessage.from("Hello"));

        Content result = GoogleGenAiContentMapper.toSystemInstruction(messages);

        assertThat(result).isNotNull();
        assertThat(result.parts().get().get(0).text().get()).isEqualTo("First instruction\nSecond instruction");
    }

    @Test
    void should_filter_out_system_messages() {
        List<ChatMessage> messages =
                List.of(SystemMessage.from("System"), UserMessage.from("Hello"), AiMessage.from("Hi"));

        List<Content> result = GoogleGenAiContentMapper.toContents(messages);

        assertThat(result).hasSize(2);
    }

    @Test
    void should_convert_user_message_with_text() {
        UserMessage message = UserMessage.from("Hello world");

        Content result = GoogleGenAiContentMapper.toContent(message);

        assertThat(result.role().get()).isEqualTo("user");
        assertThat(result.parts().get().get(0).text().get()).isEqualTo("Hello world");
    }

    @Test
    void should_convert_user_message_with_image_base64() {
        String base64Data = Base64.getEncoder().encodeToString("fake-image".getBytes());
        Image image =
                Image.builder().base64Data(base64Data).mimeType("image/png").build();
        UserMessage message = UserMessage.from(new ImageContent(image));

        Content result = GoogleGenAiContentMapper.toContent(message);

        assertThat(result.role().get()).isEqualTo("user");
        assertThat(result.parts().get()).hasSize(1);
    }

    @Test
    void should_convert_user_message_with_image_gs_url() {
        Image image = Image.builder().url(URI.create("gs://bucket/image.png")).build();
        UserMessage message = UserMessage.from(new ImageContent(image));

        Content result = GoogleGenAiContentMapper.toContent(message);

        assertThat(result.role().get()).isEqualTo("user");
        assertThat(result.parts().get()).hasSize(1);
    }

    @Test
    void should_convert_user_message_with_image_file_url(@TempDir Path tempDir) throws Exception {
        Path imgFile = tempDir.resolve("test.png");
        Files.write(imgFile, "fake-image-data".getBytes());

        Image image = Image.builder().url(imgFile.toUri()).build();
        UserMessage message = UserMessage.from(new ImageContent(image));

        Content result = GoogleGenAiContentMapper.toContent(message);

        assertThat(result.role().get()).isEqualTo("user");
        assertThat(result.parts().get()).hasSize(1);
    }

    @Test
    void should_convert_ai_message_with_text() {
        AiMessage message = AiMessage.from("I'm an AI");

        Content result = GoogleGenAiContentMapper.toContent(message);

        assertThat(result.role().get()).isEqualTo("model");
        assertThat(result.parts().get().get(0).text().get()).isEqualTo("I'm an AI");
    }

    @Test
    void should_convert_ai_message_with_tool_execution_requests() {
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .name("getWeather")
                .arguments("{\"city\":\"London\"}")
                .build();
        AiMessage message = AiMessage.from(toolRequest);

        Content result = GoogleGenAiContentMapper.toContent(message);

        assertThat(result.role().get()).isEqualTo("model");
        List<Part> parts = result.parts().get();
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).functionCall().get().name().get()).isEqualTo("getWeather");
    }

    @Test
    void should_convert_ai_message_with_empty_tool_arguments() {
        ToolExecutionRequest toolRequest =
                ToolExecutionRequest.builder().name("doSomething").arguments("").build();
        AiMessage message = AiMessage.from(toolRequest);

        Content result = GoogleGenAiContentMapper.toContent(message);

        assertThat(result.role().get()).isEqualTo("model");
        assertThat(result.parts().get().get(0).functionCall().get().name().get())
                .isEqualTo("doSomething");
    }

    @Test
    void should_throw_when_ai_message_has_invalid_json_arguments() {
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .name("doSomething")
                .arguments("not-valid-json")
                .build();
        AiMessage message = AiMessage.from(toolRequest);

        // Not asserted by exception type: the configured JSON codec decides that, and this module
        // is also run against the Jackson 3 codec, which reports a different one.
        assertThatThrownBy(() -> GoogleGenAiContentMapper.toContent(message))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(Exception.class);
    }

    @Test
    void should_convert_ai_message_with_null_tool_arguments() {
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .name("doSomething")
                .arguments(null)
                .build();
        AiMessage message = AiMessage.from(toolRequest);

        Content result = GoogleGenAiContentMapper.toContent(message);

        assertThat(result.role().get()).isEqualTo("model");
    }

    @Test
    void should_convert_parallel_tool_execution_result_messages() {
        List<ChatMessage> messages = List.of(
                ToolExecutionResultMessage.from("call-1", "getWeather", "Sunny, 25C"),
                ToolExecutionResultMessage.from("call-2", "getWeather", "Rainy, 15C"));

        List<Content> results = GoogleGenAiContentMapper.toContents(messages);

        assertThat(results).hasSize(1);
        Content result = results.get(0);
        assertThat(result.role().get()).isEqualTo("user");

        List<Part> parts = result.parts().get();
        assertThat(parts).hasSize(2);

        assertThat(parts.get(0).functionResponse().get().name().get()).isEqualTo("getWeather");
        assertThat(parts.get(0).functionResponse().get().response().get().get("result"))
                .isEqualTo("Sunny, 25C");
        assertThat(parts.get(0).functionResponse().get().id().get()).isEqualTo("call-1");

        assertThat(parts.get(1).functionResponse().get().name().get()).isEqualTo("getWeather");
        assertThat(parts.get(1).functionResponse().get().response().get().get("result"))
                .isEqualTo("Rainy, 15C");
        assertThat(parts.get(1).functionResponse().get().id().get()).isEqualTo("call-2");
    }

    @Test
    void should_throw_for_unknown_message_type() {
        ChatMessage unknownMessage = new ChatMessage() {
            @Override
            public ChatMessageType type() {
                return null;
            }
        };

        assertThatThrownBy(() -> GoogleGenAiContentMapper.toContent(unknownMessage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown message type");
    }

    @Test
    void should_return_empty_response_when_no_candidates() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(Collections.emptyList())
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        assertThat(result.aiMessage().text()).isEqualTo("Empty response");
        assertThat(result.finishReason()).isEqualTo(OTHER);
        assertThat(result.tokenUsage().inputTokenCount()).isEqualTo(0);
    }

    @Test
    void should_convert_text_response() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.builder().text("Hello!").build())
                                .build())
                        .build()))
                .usageMetadata(GenerateContentResponseUsageMetadata.builder()
                        .promptTokenCount(10)
                        .candidatesTokenCount(5)
                        .build())
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        assertThat(result.aiMessage().text()).isEqualTo("Hello!");
        assertThat(result.finishReason()).isEqualTo(STOP);
        assertThat(result.tokenUsage().inputTokenCount()).isEqualTo(10);
        assertThat(result.tokenUsage().outputTokenCount()).isEqualTo(5);
    }

    @Test
    void should_use_total_token_count_from_usage_metadata() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.builder().text("Hello!").build())
                                .build())
                        .build()))
                .usageMetadata(GenerateContentResponseUsageMetadata.builder()
                        .promptTokenCount(10)
                        .candidatesTokenCount(5)
                        .thoughtsTokenCount(7)
                        .totalTokenCount(22)
                        .build())
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        assertThat(result.tokenUsage().inputTokenCount()).isEqualTo(10);
        assertThat(result.tokenUsage().outputTokenCount()).isEqualTo(5);
        assertThat(result.tokenUsage().totalTokenCount()).isEqualTo(22);
    }

    @Test
    void should_fall_back_to_prompt_plus_candidates_when_total_token_count_absent() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.builder().text("Hello!").build())
                                .build())
                        .build()))
                .usageMetadata(GenerateContentResponseUsageMetadata.builder()
                        .promptTokenCount(10)
                        .candidatesTokenCount(5)
                        .build())
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        assertThat(result.tokenUsage().inputTokenCount()).isEqualTo(10);
        assertThat(result.tokenUsage().outputTokenCount()).isEqualTo(5);
        assertThat(result.tokenUsage().totalTokenCount()).isEqualTo(15);
    }

    @Test
    void should_return_google_gen_ai_metadata_with_raw_response() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.builder().text("Hello!").build())
                                .build())
                        .build()))
                .usageMetadata(GenerateContentResponseUsageMetadata.builder()
                        .promptTokenCount(10)
                        .candidatesTokenCount(5)
                        .build())
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        assertThat(result.metadata()).isInstanceOf(GoogleGenAiChatResponseMetadata.class);
        GoogleGenAiChatResponseMetadata metadata = (GoogleGenAiChatResponseMetadata) result.metadata();
        assertThat(metadata.rawResponse()).isSameAs(response);
        assertThat(metadata.tokenUsage().inputTokenCount()).isEqualTo(10);
        assertThat(metadata.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_convert_function_call_response() {
        Map<String, Object> args = new HashMap<>();
        args.put("city", "London");

        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.builder()
                                        .functionCall(FunctionCall.builder()
                                                .name("getWeather")
                                                .args(args)
                                                .build())
                                        .build())
                                .build())
                        .build()))
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        assertThat(result.aiMessage().toolExecutionRequests()).hasSize(1);
        assertThat(result.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("getWeather");
    }

    @Test
    void should_convert_response_with_both_text_and_function_call() {
        Map<String, Object> args = new HashMap<>();
        args.put("city", "London");

        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(
                                        Part.builder()
                                                .text("Let me check the weather.")
                                                .build(),
                                        Part.builder()
                                                .functionCall(FunctionCall.builder()
                                                        .name("getWeather")
                                                        .args(args)
                                                        .build())
                                                .build())
                                .build())
                        .build()))
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        assertThat(result.aiMessage().text()).isEqualTo("Let me check the weather.");
        assertThat(result.aiMessage().toolExecutionRequests()).hasSize(1);
    }

    @Test
    void should_return_tool_execution_finish_reason_when_response_contains_function_call() {
        Map<String, Object> args = new HashMap<>();
        args.put("city", "London");

        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.builder()
                                        .functionCall(FunctionCall.builder()
                                                .name("getWeather")
                                                .args(args)
                                                .build())
                                        .build())
                                .build())
                        .finishReason(new FinishReason(Known.STOP))
                        .build()))
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        assertThat(result.finishReason()).isEqualTo(TOOL_EXECUTION);
    }

    @Test
    void should_keep_reported_finish_reason_when_response_contains_no_function_call() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.builder().text("Hello!").build())
                                .build())
                        .finishReason(new FinishReason(Known.STOP))
                        .build()))
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        assertThat(result.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_handle_response_with_no_content() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder().build()))
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        assertThat(result.aiMessage().text()).isEmpty();
        assertThat(result.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_handle_response_without_usage_metadata() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.builder().text("Hello").build())
                                .build())
                        .build()))
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        assertThat(result.tokenUsage().inputTokenCount()).isEqualTo(0);
        assertThat(result.tokenUsage().outputTokenCount()).isEqualTo(0);
    }

    @Test
    void should_detect_image_mime_types() {
        assertThat(GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.jpg")))
                .isEqualTo("image/jpeg");
        assertThat(GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.png")))
                .isEqualTo("image/png");
        assertThat(GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.gif")))
                .isEqualTo("image/gif");
        assertThat(GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.webp")))
                .isEqualTo("image/webp");
    }

    @Test
    void should_detect_audio_mime_types() {
        assertThat(GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.mp3")))
                .isEqualTo("audio/mp3");
        assertThat(GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.wav")))
                .isEqualTo("audio/wav");
    }

    @Test
    void should_detect_video_mime_types() {
        assertThat(GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.mp4")))
                .isEqualTo("video/mp4");
        assertThat(GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.avi")))
                .isEqualTo("video/avi");
    }

    @Test
    void should_detect_document_mime_types() {
        assertThat(GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.pdf")))
                .isEqualTo("application/pdf");
        assertThat(GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.csv")))
                .isEqualTo("text/plain");
    }

    @Test
    void should_throw_for_unknown_extension() {
        assertThatThrownBy(() -> GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.xyz")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to detect the MIME type");
    }

    @Test
    void should_throw_for_url_without_extension() {
        assertThatThrownBy(() -> GoogleGenAiContentMapper.detectMimeType(URI.create("file:///testfile")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unable to detect the MIME type");
    }

    @Test
    void should_create_part_from_bytes() {
        Part result = GoogleGenAiContentMapper.fromMimeTypeAndData("image/png", "test".getBytes());

        assertThat(result).isNotNull();
    }

    @Test
    void should_create_part_from_uri() {
        Part result = GoogleGenAiContentMapper.fromMimeTypeAndData("image/png", URI.create("gs://bucket/image.png"));

        assertThat(result).isNotNull();
    }

    @Test
    void should_throw_when_reading_nonexistent_file() {
        Image image =
                Image.builder().url(URI.create("file:///nonexistent/file.png")).build();
        UserMessage message = UserMessage.from(new ImageContent(image));

        assertThatThrownBy(() -> GoogleGenAiContentMapper.toContent(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("nonexistent")
                .hasMessageContaining("file.png");
    }

    @Test
    void should_convert_user_message_with_image_url_and_explicit_mime_type(@TempDir Path tempDir) throws Exception {
        Path imgFile = tempDir.resolve("test.dat");
        Files.write(imgFile, "fake-image-data".getBytes());

        Image image = Image.builder().url(imgFile.toUri()).mimeType("image/png").build();
        UserMessage message = UserMessage.from(new ImageContent(image));

        Content result = GoogleGenAiContentMapper.toContent(message);

        assertThat(result.parts().get()).hasSize(1);
    }

    @Test
    void should_convert_user_message_with_mixed_content() {
        String base64Data = Base64.getEncoder().encodeToString("fake-image".getBytes());
        Image image =
                Image.builder().base64Data(base64Data).mimeType("image/jpeg").build();

        UserMessage message = UserMessage.from(new TextContent("Describe this image"), new ImageContent(image));

        Content result = GoogleGenAiContentMapper.toContent(message);

        assertThat(result.parts().get()).hasSize(2);
        assertThat(result.parts().get().get(0).text().get()).isEqualTo("Describe this image");
    }

    @Test
    void should_map_finish_reason() {
        assertThat(GoogleGenAiContentMapper.mapFinishReason(new FinishReason(Known.STOP)))
                .isEqualTo(STOP);

        assertThat(GoogleGenAiContentMapper.mapFinishReason(new FinishReason(Known.MAX_TOKENS)))
                .isEqualTo(LENGTH);

        assertThat(GoogleGenAiContentMapper.mapFinishReason(new FinishReason(Known.IMAGE_RECITATION)))
                .isEqualTo(CONTENT_FILTER);

        assertThat(GoogleGenAiContentMapper.mapFinishReason(new FinishReason(Known.SAFETY)))
                .isEqualTo(CONTENT_FILTER);

        assertThat(GoogleGenAiContentMapper.mapFinishReason(new FinishReason(Known.OTHER)))
                .isEqualTo(OTHER);

        assertThat(GoogleGenAiContentMapper.mapFinishReason(new FinishReason(Known.TOO_MANY_TOOL_CALLS)))
                .isEqualTo(OTHER);
    }

    @Test
    void should_map_null_finish_reason_to_other() {
        assertThat(GoogleGenAiContentMapper.mapFinishReason(null)).isEqualTo(OTHER);
    }

    private static GenerateContentResponse responseWithThoughtAndAnswer() {
        return GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(List.of(
                                        Part.builder()
                                                .text("Let me work through this.")
                                                .thought(true)
                                                .build(),
                                        Part.builder().text("42").build()))
                                .build())
                        .build()))
                .build();
    }

    @Test
    void should_return_thought_summary_in_thinking_when_return_thinking_is_true() {
        ChatResponse result =
                GoogleGenAiContentMapper.toChatResponse(responseWithThoughtAndAnswer(), "test-model", true);

        assertThat(result.aiMessage().thinking()).isEqualTo("Let me work through this.");
        assertThat(result.aiMessage().text()).isEqualTo("42");
    }

    @Test
    void should_drop_thought_summary_when_return_thinking_is_false() {
        ChatResponse result =
                GoogleGenAiContentMapper.toChatResponse(responseWithThoughtAndAnswer(), "test-model", false);

        assertThat(result.aiMessage().thinking()).isNull();
        assertThat(result.aiMessage().text()).isEqualTo("42");
    }

    @Test
    void should_drop_thought_summary_when_return_thinking_is_not_set() {
        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(responseWithThoughtAndAnswer(), "test-model");

        assertThat(result.aiMessage().thinking()).isNull();
        assertThat(result.aiMessage().text()).isEqualTo("42");
    }

    @Test
    void should_concatenate_multiple_thought_parts() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(List.of(
                                        Part.builder().text("foo").thought(true).build(),
                                        Part.builder().text("bar").thought(true).build(),
                                        Part.builder().text("answer").build()))
                                .build())
                        .build()))
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model", true);

        assertThat(result.aiMessage().thinking()).isEqualTo("foobar");
        assertThat(result.aiMessage().text()).isEqualTo("answer");
    }

    @Test
    void should_treat_part_without_thought_flag_as_regular_text() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(List.of(Part.builder().text("plain").build()))
                                .build())
                        .build()))
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model", true);

        assertThat(result.aiMessage().thinking()).isNull();
        assertThat(result.aiMessage().text()).isEqualTo("plain");
    }

    @Test
    void should_return_thought_summary_alongside_tool_call_and_signature() {
        FunctionCall functionCall = FunctionCall.builder()
                .name("get_weather")
                .id("call_1")
                .args(Map.of("city", "Paris"))
                .build();

        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(List.of(
                                        Part.builder()
                                                .text("I should call the tool.")
                                                .thought(true)
                                                .build(),
                                        Part.builder()
                                                .functionCall(functionCall)
                                                .thoughtSignature("sig".getBytes())
                                                .build()))
                                .build())
                        .build()))
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model", true);

        assertThat(result.aiMessage().thinking()).isEqualTo("I should call the tool.");
        assertThat(result.aiMessage().hasToolExecutionRequests()).isTrue();
        assertThat(result.aiMessage().attribute("thought_signature_call_1", String.class))
                .isEqualTo(Base64.getEncoder().encodeToString("sig".getBytes()));
    }

    @Test
    void should_send_thinking_as_thought_part_before_text_when_send_thinking_is_true() {
        AiMessage message = AiMessage.builder()
                .text("The answer is 42.")
                .thinking("Working it out.")
                .build();

        Content result = GoogleGenAiContentMapper.toContent(message, true);

        List<Part> parts = result.parts().orElseThrow();
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).text()).hasValue("Working it out.");
        assertThat(parts.get(0).thought()).hasValue(true);
        assertThat(parts.get(1).text()).hasValue("The answer is 42.");
        assertThat(parts.get(1).thought()).isEmpty();
    }

    @Test
    void should_not_send_thinking_when_send_thinking_is_false() {
        AiMessage message = AiMessage.builder()
                .text("The answer is 42.")
                .thinking("Working it out.")
                .build();

        Content result = GoogleGenAiContentMapper.toContent(message, false);

        List<Part> parts = result.parts().orElseThrow();
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).text()).hasValue("The answer is 42.");
    }

    @Test
    void should_not_add_thought_part_when_message_has_no_thinking() {
        AiMessage message = AiMessage.from("The answer is 42.");

        Content result = GoogleGenAiContentMapper.toContent(message, true);

        List<Part> parts = result.parts().orElseThrow();
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).thought()).isEmpty();
    }

    @Test
    void should_reattach_function_call_thought_signature_when_send_thinking_is_false() {
        AiMessage message = AiMessage.builder()
                .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                        .id("call_1")
                        .name("get_weather")
                        .arguments("{\"city\":\"Paris\"}")
                        .build()))
                .attributes(
                        Map.of("thought_signature_call_1", Base64.getEncoder().encodeToString("sig".getBytes())))
                .build();

        Content result = GoogleGenAiContentMapper.toContent(message, false);

        List<Part> parts = result.parts().orElseThrow();
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).thoughtSignature()).hasValue("sig".getBytes());
    }

    @Test
    void should_preserve_empty_text_part_when_send_thinking_is_false() {
        AiMessage message = AiMessage.from("");

        Content result = GoogleGenAiContentMapper.toContent(message, false);

        List<Part> parts = result.parts().orElseThrow();
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).text()).hasValue("");
        assertThat(parts.get(0).thought()).isEmpty();
    }

    @Test
    void should_map_cached_content_and_thoughts_token_counts() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .parts(Part.builder().text("42").build())
                                .build())
                        .build()))
                .usageMetadata(GenerateContentResponseUsageMetadata.builder()
                        .promptTokenCount(10)
                        .candidatesTokenCount(5)
                        .totalTokenCount(40)
                        .cachedContentTokenCount(7)
                        .thoughtsTokenCount(25)
                        .toolUsePromptTokenCount(3)
                        .build())
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        GoogleGenAiTokenUsage tokenUsage = (GoogleGenAiTokenUsage) result.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(10);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(5);
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(40);
        assertThat(tokenUsage.cachedContentTokenCount()).isEqualTo(7);
        assertThat(tokenUsage.thoughtsTokenCount()).isEqualTo(25);
        assertThat(tokenUsage.toolUsePromptTokenCount()).isEqualTo(3);
    }

    @Test
    void should_map_usage_metadata_when_candidates_are_empty() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .usageMetadata(GenerateContentResponseUsageMetadata.builder()
                        .promptTokenCount(10)
                        .candidatesTokenCount(0)
                        .totalTokenCount(35)
                        .cachedContentTokenCount(7)
                        .thoughtsTokenCount(25)
                        .build())
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        GoogleGenAiTokenUsage tokenUsage = (GoogleGenAiTokenUsage) result.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(10);
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(35);
        assertThat(tokenUsage.cachedContentTokenCount()).isEqualTo(7);
        assertThat(tokenUsage.thoughtsTokenCount()).isEqualTo(25);
    }

    @Test
    void should_include_thoughts_and_tool_use_tokens_in_the_fallback_total() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .parts(Part.builder().text("42").build())
                                .build())
                        .build()))
                .usageMetadata(GenerateContentResponseUsageMetadata.builder()
                        .promptTokenCount(10)
                        .candidatesTokenCount(5)
                        .thoughtsTokenCount(25)
                        .toolUsePromptTokenCount(3)
                        .build())
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        assertThat(result.tokenUsage().totalTokenCount()).isEqualTo(43);
    }

    @Test
    void should_leave_cached_content_and_thoughts_token_counts_null_when_not_reported() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .parts(Part.builder().text("42").build())
                                .build())
                        .build()))
                .usageMetadata(GenerateContentResponseUsageMetadata.builder()
                        .promptTokenCount(10)
                        .candidatesTokenCount(5)
                        .build())
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        GoogleGenAiTokenUsage tokenUsage = (GoogleGenAiTokenUsage) result.tokenUsage();
        assertThat(tokenUsage.cachedContentTokenCount()).isNull();
        assertThat(tokenUsage.thoughtsTokenCount()).isNull();
        assertThat(tokenUsage.toolUsePromptTokenCount()).isNull();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(15);
    }

    @Test
    void should_capture_thought_signature_from_a_text_part() {
        byte[] signature = "text-signature".getBytes();
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .parts(List.of(
                                        Part.builder()
                                                .text("Working it out.")
                                                .thought(true)
                                                .build(),
                                        Part.builder()
                                                .text("42")
                                                .thoughtSignature(signature)
                                                .build()))
                                .build())
                        .build()))
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        assertThat(result.aiMessage().attribute("thought_signature", String.class))
                .isEqualTo(Base64.getEncoder().encodeToString(signature));
    }

    @Test
    void should_send_back_the_thought_signature_on_the_text_part() {
        byte[] signature = "text-signature".getBytes();
        AiMessage message = AiMessage.builder()
                .text("42")
                .attributes(Map.of("thought_signature", Base64.getEncoder().encodeToString(signature)))
                .build();

        Content result = GoogleGenAiContentMapper.toContent(message, true);

        List<Part> parts = result.parts().orElseThrow();
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).text()).hasValue("42");
        assertThat(parts.get(0).thoughtSignature()).hasValue(signature);
    }

    @Test
    void should_not_send_back_the_thought_signature_when_thinking_is_not_sent() {
        String signature = Base64.getEncoder().encodeToString("text-signature".getBytes());
        AiMessage message = AiMessage.builder()
                .text("42")
                .attributes(Map.of("thought_signature", signature))
                .build();

        Content result = GoogleGenAiContentMapper.toContent(message, false);

        List<Part> parts = result.parts().orElseThrow();
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).text()).hasValue("42");
        assertThat(parts.get(0).thoughtSignature()).isEmpty();
    }

    @Test
    void should_round_trip_the_thought_signature_of_a_text_part() {
        byte[] signature = "text-signature".getBytes();
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .parts(Part.builder()
                                        .text("42")
                                        .thoughtSignature(signature)
                                        .build())
                                .build())
                        .build()))
                .build();

        AiMessage aiMessage =
                GoogleGenAiContentMapper.toChatResponse(response, "test-model").aiMessage();
        Content result = GoogleGenAiContentMapper.toContent(aiMessage, true);

        assertThat(result.parts().orElseThrow().get(0).thoughtSignature()).hasValue(signature);
    }

    @Test
    void should_not_capture_a_thought_signature_from_a_part_that_is_not_the_last() {
        byte[] signature = "thinking-signature".getBytes();
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .parts(List.of(
                                        Part.builder()
                                                .text("Working it out.")
                                                .thought(true)
                                                .thoughtSignature(signature)
                                                .build(),
                                        Part.builder().text("42").build()))
                                .build())
                        .build()))
                .build();

        AiMessage aiMessage = GoogleGenAiContentMapper.toChatResponse(response, "test-model", true)
                .aiMessage();

        assertThat(aiMessage.attributes()).doesNotContainKey("thought_signature");
        assertThat(GoogleGenAiContentMapper.toContent(aiMessage, true).parts().orElseThrow())
                .allSatisfy(part -> assertThat(part.thoughtSignature()).isEmpty());
    }

    @Test
    void should_not_capture_a_thought_signature_when_the_part_has_none() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .parts(Part.builder().text("42").build())
                                .build())
                        .build()))
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        assertThat(result.aiMessage().attributes()).doesNotContainKey("thought_signature");
    }

    @Test
    void should_keep_the_function_call_thought_signature_keyed_by_tool_call_id() {
        byte[] signature = "call-signature".getBytes();
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .parts(Part.builder()
                                        .functionCall(FunctionCall.builder()
                                                .id("call-1")
                                                .name("getWeather")
                                                .args(Map.of())
                                                .build())
                                        .thoughtSignature(signature)
                                        .build())
                                .build())
                        .build()))
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        assertThat(result.aiMessage().attribute("thought_signature_call-1", String.class))
                .isEqualTo(Base64.getEncoder().encodeToString(signature));
        assertThat(result.aiMessage().attributes()).doesNotContainKey("thought_signature");
    }

    @Test
    void should_extract_text_from_audio_transcription_part() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .parts(Part.builder()
                                        .audioTranscription(Transcription.builder()
                                                .text("Hello world transcription")
                                                .build())
                                        .build())
                                .build())
                        .build()))
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "gemini-3.5-transcribe");

        assertThat(result.aiMessage().text()).isEqualTo("Hello world transcription");
    }

    @Test
    void should_separate_audio_transcription_segments_with_a_space() {
        GenerateContentResponse response = responseWithParts(
                transcriptionPart(Transcription.builder().text("Good evening.").build()),
                transcriptionPart(Transcription.builder().text("Good morning.").build()));

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "gemini-3.5-transcribe");

        assertThat(result.aiMessage().text()).isEqualTo("Good evening. Good morning.");
    }

    @Test
    void should_not_add_a_space_when_audio_transcription_segments_are_already_separated() {
        GenerateContentResponse response = responseWithParts(
                transcriptionPart(Transcription.builder().text("Good evening. ").build()),
                transcriptionPart(Transcription.builder().text("Good morning.").build()));

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "gemini-3.5-transcribe");

        assertThat(result.aiMessage().text()).isEqualTo("Good evening. Good morning.");
    }

    @Test
    void should_build_text_from_words_when_audio_transcription_has_no_text() {
        GenerateContentResponse response = responseWithParts(
                transcriptionPart(Transcription.builder()
                        .speakerLabel("spk_1")
                        .words(
                                WordInfo.builder()
                                        .word("Hello")
                                        .startOffset("0.100s")
                                        .endOffset("0.450s")
                                        .build(),
                                WordInfo.builder()
                                        .word("world")
                                        .startOffset("0.500s")
                                        .endOffset("0.850s")
                                        .build())
                        .build()),
                transcriptionPart(Transcription.builder()
                        .speakerLabel("spk_2")
                        .words(WordInfo.builder().word("Hi").build())
                        .build()));

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "gemini-3.5-transcribe");

        assertThat(result.aiMessage().text()).isEqualTo("Hello world Hi");
    }

    @Test
    void should_collect_a_generated_image_from_inline_data() {
        byte[] pngBytes = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(
                responseWithParts(imagePart(pngBytes, "image/png")), "gemini-2.5-flash-image");

        assertThat(result.aiMessage().images()).hasSize(1);
        Image generatedImage = result.aiMessage().images().get(0);
        assertThat(generatedImage.mimeType()).isEqualTo("image/png");
        assertThat(generatedImage.base64Data()).isEqualTo(Base64.getEncoder().encodeToString(pngBytes));
    }

    @Test
    void should_collect_every_generated_image_returned_in_one_response() {
        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(
                responseWithParts(
                        imagePart("first".getBytes(StandardCharsets.UTF_8), "image/png"),
                        imagePart("second".getBytes(StandardCharsets.UTF_8), "image/jpeg")),
                "gemini-2.5-flash-image");

        assertThat(result.aiMessage().images()).hasSize(2);
        assertThat(result.aiMessage().images().get(0).mimeType()).isEqualTo("image/png");
        assertThat(result.aiMessage().images().get(1).mimeType()).isEqualTo("image/jpeg");
    }

    @Test
    void should_keep_text_alongside_a_generated_image() {
        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(
                responseWithParts(
                        Part.builder().text("Here is your picture").build(),
                        imagePart("bytes".getBytes(StandardCharsets.UTF_8), "image/png")),
                "gemini-2.5-flash-image");

        assertThat(result.aiMessage().text()).isEqualTo("Here is your picture");
        assertThat(result.aiMessage().images()).hasSize(1);
    }

    @Test
    void should_ignore_inline_data_that_is_not_an_image() {
        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(
                responseWithParts(imagePart("audio-bytes".getBytes(StandardCharsets.UTF_8), "audio/mpeg")),
                "gemini-2.5-flash-image");

        assertThat(result.aiMessage().images()).isEmpty();
    }

    @Test
    void should_ignore_inline_data_without_a_mime_type() {
        Blob blob =
                Blob.builder().data("bytes".getBytes(StandardCharsets.UTF_8)).build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(
                responseWithParts(Part.builder().inlineData(blob).build()), "gemini-2.5-flash-image");

        assertThat(result.aiMessage().images()).isEmpty();
        assertThat(result.aiMessage().audios()).isEmpty();
    }

    @Test
    void should_collect_generated_audio_from_inline_data() {
        byte[] wavBytes = "fake-wav-bytes".getBytes(StandardCharsets.UTF_8);

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(
                responseWithParts(imagePart(wavBytes, "audio/wav")), "gemini-3.8-flash-tts");

        assertThat(result.aiMessage().images()).isEmpty();
        List<Audio> generatedAudio = result.aiMessage().audios();
        assertThat(generatedAudio).hasSize(1);
        assertThat(generatedAudio.get(0).mimeType()).isEqualTo("audio/wav");
        assertThat(generatedAudio.get(0).base64Data())
                .isEqualTo(Base64.getEncoder().encodeToString(wavBytes));
    }

    @Test
    void should_map_initial_prompt_token_count_for_multi_hop_continuation() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .parts(Part.builder().text("Continued answer").build())
                                .build())
                        .build()))
                .usageMetadata(GenerateContentResponseUsageMetadata.builder()
                        .promptTokenCount(30)
                        .firstHopPromptTokenCount(12)
                        .candidatesTokenCount(15)
                        .totalTokenCount(45)
                        .build())
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        GoogleGenAiTokenUsage tokenUsage = (GoogleGenAiTokenUsage) result.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(30);
        assertThat(tokenUsage.initialPromptTokenCount()).isEqualTo(12);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(15);
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(45);
    }

    @Test
    void should_preserve_audio_transcription_segment_start_and_end_offsets() {
        Transcription firstSegment = Transcription.builder()
                .text("First segment.")
                .speakerLabel("spk_1")
                .startOffset(Duration.ofMillis(100))
                .endOffset(Duration.ofMillis(1500))
                .build();
        Transcription secondSegment = Transcription.builder()
                .text("Second segment.")
                .speakerLabel("spk_2")
                .startOffset(Duration.ofMillis(1600))
                .endOffset(Duration.ofMillis(3200))
                .build();

        GenerateContentResponse response =
                responseWithParts(transcriptionPart(firstSegment), transcriptionPart(secondSegment));

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "gemini-3.5-transcribe");

        assertThat(result.aiMessage().text()).isEqualTo("First segment. Second segment.");
        GoogleGenAiChatResponseMetadata metadata = (GoogleGenAiChatResponseMetadata) result.metadata();
        List<Part> rawParts = metadata.rawResponse()
                .candidates()
                .orElseThrow()
                .get(0)
                .content()
                .orElseThrow()
                .parts()
                .orElseThrow();
        assertThat(rawParts.get(0).audioTranscription().orElseThrow().startOffset())
                .hasValue(Duration.ofMillis(100));
        assertThat(rawParts.get(0).audioTranscription().orElseThrow().endOffset())
                .hasValue(Duration.ofMillis(1500));
        assertThat(rawParts.get(1).audioTranscription().orElseThrow().startOffset())
                .hasValue(Duration.ofMillis(1600));
        assertThat(rawParts.get(1).audioTranscription().orElseThrow().endOffset())
                .hasValue(Duration.ofMillis(3200));
    }

    @Test
    void should_convert_multimodal_tool_execution_result_message_with_text_and_image() {
        String base64Png = Base64.getEncoder().encodeToString("png-bytes".getBytes(StandardCharsets.UTF_8));
        ToolExecutionResultMessage toolMessage = ToolExecutionResultMessage.builder()
                .id("call-42")
                .toolName("take_screenshot")
                .contents(List.of(TextContent.from("Screenshot captured"), ImageContent.from(base64Png, "image/png")))
                .build();

        List<Content> contents = GoogleGenAiContentMapper.toContents(List.of(toolMessage), false);

        assertThat(contents).hasSize(1);
        Content content = contents.get(0);
        assertThat(content.role()).hasValue("user");
        List<Part> parts = content.parts().orElseThrow();
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).functionResponse()).isPresent();
        assertThat(parts.get(0).functionResponse().get().id()).hasValue("call-42");
        assertThat(parts.get(0).functionResponse().get().name()).hasValue("take_screenshot");
        assertThat(parts.get(0).functionResponse().get().response()).hasValue(Map.of("result", "Screenshot captured"));
        assertThat(parts.get(1).inlineData()).isPresent();
        assertThat(parts.get(1).inlineData().get().mimeType()).hasValue("image/png");
    }

    @Test
    void should_convert_multimodal_tool_execution_result_message_with_image_only() {
        String base64Png = Base64.getEncoder().encodeToString("png-bytes".getBytes(StandardCharsets.UTF_8));
        ToolExecutionResultMessage toolMessage = ToolExecutionResultMessage.builder()
                .id("call-43")
                .toolName("take_screenshot")
                .contents(List.of(ImageContent.from(base64Png, "image/png")))
                .build();

        List<Content> contents = GoogleGenAiContentMapper.toContents(List.of(toolMessage), false);

        assertThat(contents).hasSize(1);
        List<Part> parts = contents.get(0).parts().orElseThrow();
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).functionResponse().orElseThrow().response()).hasValue(Map.of("result", ""));
        assertThat(parts.get(1).inlineData()).isPresent();
    }

    @Test
    void should_join_all_texts_of_multimodal_tool_execution_result_message() {
        String base64Png = Base64.getEncoder().encodeToString("png-bytes".getBytes(StandardCharsets.UTF_8));
        ToolExecutionResultMessage toolMessage = ToolExecutionResultMessage.builder()
                .id("call-45")
                .toolName("take_screenshot")
                .contents(List.of(
                        TextContent.from("Screenshot captured"),
                        ImageContent.from(base64Png, "image/png"),
                        TextContent.from("Resolution: 1920x1080")))
                .build();

        List<Content> contents = GoogleGenAiContentMapper.toContents(List.of(toolMessage), false);

        List<Part> parts = contents.get(0).parts().orElseThrow();
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).functionResponse().orElseThrow().response())
                .hasValue(Map.of("result", "Screenshot captured\nResolution: 1920x1080"));
        assertThat(parts.get(1).inlineData()).isPresent();
    }

    @Test
    void should_reject_unsupported_content_in_tool_execution_result_message() {
        String base64Wav = Base64.getEncoder().encodeToString("wav-bytes".getBytes(StandardCharsets.UTF_8));
        ToolExecutionResultMessage toolMessage = ToolExecutionResultMessage.builder()
                .id("call-44")
                .toolName("record_audio")
                .contents(List.of(AudioContent.from(base64Wav, "audio/wav")))
                .build();

        assertThatThrownBy(() -> GoogleGenAiContentMapper.toContents(List.of(toolMessage), false))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("does not support non-text content type 'AUDIO'");
    }

    @Test
    void should_add_executed_code_and_its_output_to_text_when_requested() {
        GenerateContentResponse response = responseWithParts(
                codePart("PYTHON", "print(2**100)\n"),
                resultPart("OUTCOME_OK", "1267650600228229401496703205376\n"),
                Part.fromText("2**100 is 1267650600228229401496703205376."));

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model", false, true);

        assertThat(result.aiMessage().text())
                .isEqualTo("Code executed:\n```python\nprint(2**100)\n```\n\n"
                        + "Output:\n```\n1267650600228229401496703205376\n```\n\n"
                        + "2**100 is 1267650600228229401496703205376.");
    }

    @Test
    void should_leave_executed_code_out_of_text_by_default() {
        GenerateContentResponse response = responseWithParts(
                codePart("PYTHON", "print(2**100)\n"),
                resultPart("OUTCOME_OK", "1267650600228229401496703205376\n"),
                Part.fromText("2**100 is 1267650600228229401496703205376."));

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model", false);

        assertThat(result.aiMessage().text()).isEqualTo("2**100 is 1267650600228229401496703205376.");
    }

    @Test
    void should_add_outcome_and_error_output_of_failed_code() {
        GenerateContentResponse response = responseWithParts(
                codePart("PYTHON", "print(1/0)"),
                resultPart("OUTCOME_FAILED", "ZeroDivisionError: division by zero\n"),
                Part.fromText("The code divides by zero."));

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model", false, true);

        assertThat(result.aiMessage().text())
                .isEqualTo("Code executed:\n```python\nprint(1/0)\n```\n\n"
                        + "Code execution failed: **OUTCOME_FAILED**\n```\nZeroDivisionError: division by zero\n```\n\n"
                        + "The code divides by zero.");
    }

    @Test
    void should_add_outcome_of_timed_out_code_without_output() {
        GenerateContentResponse response =
                responseWithParts(resultPart("OUTCOME_DEADLINE_EXCEEDED", null), Part.fromText("It took too long."));

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model", false, true);

        assertThat(result.aiMessage().text())
                .isEqualTo("Code execution failed: **OUTCOME_DEADLINE_EXCEEDED**\n\nIt took too long.");
    }

    @Test
    void should_close_code_block_when_output_has_no_trailing_new_line() {
        GenerateContentResponse response = responseWithParts(resultPart("OUTCOME_OK", "42"));

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model", false, true);

        assertThat(result.aiMessage().text()).isEqualTo("Output:\n```\n42\n```\n\n");
    }

    @Test
    void should_use_a_longer_fence_when_output_contains_backticks() {
        GenerateContentResponse response = responseWithParts(resultPart("OUTCOME_OK", "```\nx = 1\n```\n"));

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model", false, true);

        assertThat(result.aiMessage().text()).isEqualTo("Output:\n````\n```\nx = 1\n```\n````\n\n");
    }

    @Test
    void should_omit_empty_output_of_successful_code_and_keep_the_chart() {
        GenerateContentResponse response = responseWithParts(
                codePart("PYTHON", "plt.show()"),
                resultPart("OUTCOME_OK", ""),
                imagePart(new byte[] {1, 2, 3}, "image/png"),
                Part.fromText("Here is the chart."));

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model", false, true);

        assertThat(result.aiMessage().text())
                .isEqualTo("Code executed:\n```python\nplt.show()\n```\n\nHere is the chart.");
        assertThat(result.aiMessage().images()).hasSize(1);
    }

    @Test
    void should_use_python_when_language_is_missing_or_unspecified() {
        GenerateContentResponse unspecified = responseWithParts(codePart("LANGUAGE_UNSPECIFIED", "x = 1"));
        GenerateContentResponse missing = responseWithParts(Part.builder()
                .executableCode(ExecutableCode.builder().code("x = 1"))
                .build());

        assertThat(GoogleGenAiContentMapper.toChatResponse(unspecified, "test-model", false, true)
                        .aiMessage()
                        .text())
                .isEqualTo("Code executed:\n```python\nx = 1\n```\n\n");
        assertThat(GoogleGenAiContentMapper.toChatResponse(missing, "test-model", false, true)
                        .aiMessage()
                        .text())
                .isEqualTo("Code executed:\n```python\nx = 1\n```\n\n");
    }

    @Test
    void should_keep_the_language_reported_for_the_code() {
        GenerateContentResponse response = responseWithParts(codePart("JAVASCRIPT", "console.log(1)"));

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model", false, true);

        assertThat(result.aiMessage().text()).isEqualTo("Code executed:\n```javascript\nconsole.log(1)\n```\n\n");
    }

    @Test
    void should_keep_thought_signature_of_last_part_when_code_part_has_one() {
        GenerateContentResponse response = responseWithParts(
                Part.builder()
                        .executableCode(
                                ExecutableCode.builder().language("PYTHON").code("x = 1"))
                        .thoughtSignature("code-signature".getBytes())
                        .build(),
                resultPart("OUTCOME_OK", "1\n"),
                Part.builder()
                        .text("x is 1.")
                        .thoughtSignature("answer-signature".getBytes())
                        .build());

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model", false, true);

        assertThat(result.aiMessage().attribute("thought_signature", String.class))
                .isEqualTo(Base64.getEncoder().encodeToString("answer-signature".getBytes()));
    }

    @Test
    void should_separate_executed_code_from_preceding_text() {
        GenerateContentResponse response = responseWithParts(
                Part.fromText("Let me calculate that with Python:"),
                codePart("PYTHON", "print(6 * 7)"),
                resultPart("OUTCOME_OK", "42\n"),
                Part.fromText("6 times 7 is 42."));

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model", false, true);

        assertThat(result.aiMessage().text())
                .isEqualTo("Let me calculate that with Python:\n\n"
                        + "Code executed:\n```python\nprint(6 * 7)\n```\n\n"
                        + "Output:\n```\n42\n```\n\n"
                        + "6 times 7 is 42.");
    }

    @Test
    void should_complete_the_blank_line_before_executed_code() {
        GenerateContentResponse oneNewLine = responseWithParts(Part.fromText("First:\n"), codePart("PYTHON", "x = 1"));
        GenerateContentResponse blankLine = responseWithParts(Part.fromText("First:\n\n"), codePart("PYTHON", "x = 1"));

        assertThat(GoogleGenAiContentMapper.toChatResponse(oneNewLine, "test-model", false, true)
                        .aiMessage()
                        .text())
                .isEqualTo("First:\n\nCode executed:\n```python\nx = 1\n```\n\n");
        assertThat(GoogleGenAiContentMapper.toChatResponse(blankLine, "test-model", false, true)
                        .aiMessage()
                        .text())
                .isEqualTo("First:\n\nCode executed:\n```python\nx = 1\n```\n\n");
    }

    @Test
    void should_separate_each_round_of_code_execution_from_text_in_between() {
        GenerateContentResponse response = responseWithParts(
                codePart("PYTHON", "print(6 * 7)"),
                resultPart("OUTCOME_OK", "42\n"),
                Part.fromText("Now the square:"),
                codePart("PYTHON", "print(42 ** 2)"),
                resultPart("OUTCOME_OK", "1764\n"));

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model", false, true);

        assertThat(result.aiMessage().text())
                .isEqualTo("Code executed:\n```python\nprint(6 * 7)\n```\n\n"
                        + "Output:\n```\n42\n```\n\n"
                        + "Now the square:\n\n"
                        + "Code executed:\n```python\nprint(42 ** 2)\n```\n\n"
                        + "Output:\n```\n1764\n```\n\n");
    }

    @Test
    void should_separate_code_output_from_preceding_text() {
        GenerateContentResponse succeeded =
                responseWithParts(Part.fromText("It printed:"), resultPart("OUTCOME_OK", "42\n"));
        GenerateContentResponse failed =
                responseWithParts(Part.fromText("It failed:"), resultPart("OUTCOME_FAILED", null));

        assertThat(GoogleGenAiContentMapper.toChatResponse(succeeded, "test-model", false, true)
                        .aiMessage()
                        .text())
                .isEqualTo("It printed:\n\nOutput:\n```\n42\n```\n\n");
        assertThat(GoogleGenAiContentMapper.toChatResponse(failed, "test-model", false, true)
                        .aiMessage()
                        .text())
                .isEqualTo("It failed:\n\nCode execution failed: **OUTCOME_FAILED**\n\n");
    }

    @Test
    void should_separate_executed_code_from_text_streamed_before_it() {
        GenerateContentResponse response = responseWithParts(codePart("PYTHON", "x = 1"));

        assertThat(GoogleGenAiContentMapper.toChatResponse(response, "test-model", false, true, "Let me check:")
                        .aiMessage()
                        .text())
                .isEqualTo("\n\nCode executed:\n```python\nx = 1\n```\n\n");
        assertThat(GoogleGenAiContentMapper.toChatResponse(response, "test-model", false, true, "Let me check:\n\n")
                        .aiMessage()
                        .text())
                .isEqualTo("Code executed:\n```python\nx = 1\n```\n\n");
    }

    private static Part codePart(String language, String code) {
        return Part.builder()
                .executableCode(ExecutableCode.builder().language(language).code(code))
                .build();
    }

    private static Part resultPart(String outcome, String output) {
        CodeExecutionResult.Builder result = CodeExecutionResult.builder().outcome(outcome);
        if (output != null) {
            result.output(output);
        }
        return Part.builder().codeExecutionResult(result).build();
    }

    private static GenerateContentResponse responseWithParts(Part... parts) {
        return GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder().parts(parts).build())
                        .build()))
                .build();
    }

    private static Part imagePart(byte[] data, String mimeType) {
        return Part.builder()
                .inlineData(Blob.builder().data(data).mimeType(mimeType).build())
                .build();
    }

    private static Part transcriptionPart(Transcription transcription) {
        return Part.builder().audioTranscription(transcription).build();
    }
}
