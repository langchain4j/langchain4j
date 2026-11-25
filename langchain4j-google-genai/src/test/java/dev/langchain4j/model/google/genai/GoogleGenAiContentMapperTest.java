package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.genai.types.Blob;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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
    void should_convert_user_message_with_image_read_from_a_local_file(@TempDir Path tempDir) throws Exception {
        Path imgFile = tempDir.resolve("test.png");
        Files.write(imgFile, "fake-image-data".getBytes());

        UserMessage message = UserMessage.from(ImageContent.from(imgFile, "image/png"));

        Content result = GoogleGenAiContentMapper.toContent(message);

        assertThat(result.role().get()).isEqualTo("user");
        Blob blob = result.parts().get().get(0).inlineData().get();
        assertThat(blob.data()).hasValue("fake-image-data".getBytes());
        assertThat(blob.mimeType()).hasValue("image/png");
    }

    static Stream<Arguments> mediaGivenByUrl() {
        return Stream.of(
                Arguments.of(ImageContent.from("https://example.com/photos/cat.png"), "image/png"),
                Arguments.of(ImageContent.from("http://example.com/photos/cat.png"), "image/png"),
                Arguments.of(AudioContent.from("https://example.com/audio/speech.mp3"), "audio/mp3"),
                Arguments.of(VideoContent.from("https://example.com/video/clip.mp4"), "video/mp4"),
                Arguments.of(PdfFileContent.from("https://example.com/docs/report.pdf"), "application/pdf"),
                Arguments.of(ImageContent.from("gs://bucket/image.png"), "image/png"),
                Arguments.of(ImageContent.from("HTTPS://example.com/photos/cat.png"), "image/png"));
    }

    @ParameterizedTest
    @MethodSource("mediaGivenByUrl")
    void should_pass_media_urls_on_to_gemini(dev.langchain4j.data.message.Content content, String mimeType) {
        Content result = GoogleGenAiContentMapper.toContent(UserMessage.from(content));

        Part part = result.parts().get().get(0);
        assertThat(part.inlineData()).isEmpty();
        assertThat(part.fileData().get().fileUri()).hasValue(urlOf(content));
        assertThat(part.fileData().get().mimeType()).hasValue(mimeType);
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///etc/hosts.png", "/home/me/cat.png", "cat.png", "ftp://example.com/cat.png"})
    void should_reject_media_given_by_other_urls(String url) {
        for (dev.langchain4j.data.message.Content content : List.of(
                ImageContent.from(url),
                AudioContent.from(url),
                VideoContent.from(url),
                PdfFileContent.from(url))) {

            assertThatThrownBy(() -> GoogleGenAiContentMapper.toContent(UserMessage.from(content)))
                    .isExactlyInstanceOf(UnsupportedFeatureException.class)
                    .hasMessage(GoogleGenAiContentMapper.URL_NOT_SUPPORTED);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"file:///tmp/imagen-image-1.png", "https://example.com/photos/cat.png"})
    void should_send_the_content_of_media_that_has_both_content_and_a_url(String url) {
        Image image = Image.builder()
                .url(url)
                .base64Data(Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}))
                .build();

        Content result = GoogleGenAiContentMapper.toContent(UserMessage.from(ImageContent.from(image)));

        Part part = result.parts().get().get(0);
        assertThat(part.fileData()).isEmpty();
        assertThat(part.inlineData().get().data()).hasValue(new byte[] {1, 2, 3});
        assertThat(part.inlineData().get().mimeType()).hasValue("image/png");
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

        assertThatThrownBy(() -> GoogleGenAiContentMapper.toContent(message))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
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
            public dev.langchain4j.data.message.ChatMessageType type() {
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
        assertThat(result.finishReason()).isEqualTo(FinishReason.OTHER);
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
        assertThat(result.finishReason()).isEqualTo(FinishReason.STOP);
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
        assertThat(metadata.finishReason()).isEqualTo(FinishReason.STOP);
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
                        .finishReason(
                                new com.google.genai.types.FinishReason(com.google.genai.types.FinishReason.Known.STOP))
                        .build()))
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        assertThat(result.finishReason()).isEqualTo(FinishReason.TOOL_EXECUTION);
    }

    @Test
    void should_keep_reported_finish_reason_when_response_contains_no_function_call() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(Part.builder().text("Hello!").build())
                                .build())
                        .finishReason(
                                new com.google.genai.types.FinishReason(com.google.genai.types.FinishReason.Known.STOP))
                        .build()))
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        assertThat(result.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_handle_response_with_no_content() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder().build()))
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response, "test-model");

        assertThat(result.aiMessage().text()).isEmpty();
        assertThat(result.finishReason()).isEqualTo(FinishReason.STOP);
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
    void should_convert_user_message_with_image_url_and_explicit_mime_type() {
        Image image = Image.builder()
                .url("https://example.com/photos?id=42")
                .mimeType("image/png")
                .build();
        UserMessage message = UserMessage.from(new ImageContent(image));

        Content result = GoogleGenAiContentMapper.toContent(message);

        assertThat(result.parts().get()).hasSize(1);
        assertThat(result.parts().get().get(0).fileData().get().fileUri()).hasValue("https://example.com/photos?id=42");
        assertThat(result.parts().get().get(0).fileData().get().mimeType()).hasValue("image/png");
    }

    private static String urlOf(dev.langchain4j.data.message.Content content) {
        if (content instanceof ImageContent image) {
            return image.image().url().toString();
        } else if (content instanceof AudioContent audio) {
            return audio.audio().url().toString();
        } else if (content instanceof VideoContent video) {
            return video.video().url().toString();
        }
        return ((PdfFileContent) content).pdfFile().url().toString();
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
        assertThat(GoogleGenAiContentMapper.mapFinishReason(
                        new com.google.genai.types.FinishReason(com.google.genai.types.FinishReason.Known.STOP)))
                .isEqualTo(FinishReason.STOP);

        assertThat(GoogleGenAiContentMapper.mapFinishReason(
                        new com.google.genai.types.FinishReason(com.google.genai.types.FinishReason.Known.MAX_TOKENS)))
                .isEqualTo(FinishReason.LENGTH);

        assertThat(GoogleGenAiContentMapper.mapFinishReason(new com.google.genai.types.FinishReason(
                        com.google.genai.types.FinishReason.Known.IMAGE_RECITATION)))
                .isEqualTo(FinishReason.CONTENT_FILTER);

        assertThat(GoogleGenAiContentMapper.mapFinishReason(
                        new com.google.genai.types.FinishReason(com.google.genai.types.FinishReason.Known.SAFETY)))
                .isEqualTo(FinishReason.CONTENT_FILTER);

        assertThat(GoogleGenAiContentMapper.mapFinishReason(
                        new com.google.genai.types.FinishReason(com.google.genai.types.FinishReason.Known.OTHER)))
                .isEqualTo(FinishReason.OTHER);
    }

    @Test
    void should_map_null_finish_reason_to_other() {
        assertThat(GoogleGenAiContentMapper.mapFinishReason(null)).isEqualTo(FinishReason.OTHER);
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
}
