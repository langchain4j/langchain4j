package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import com.google.genai.types.UsageMetadata;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoogleGenAiContentMapperTest {

    // --- toSystemInstruction ---

    @Test
    void should_return_null_when_no_system_messages() {
        List<ChatMessage> messages = List.of(UserMessage.from("Hello"));

        Content result = GoogleGenAiContentMapper.toSystemInstruction(messages);

        assertThat(result).isNull();
    }

    @Test
    void should_extract_system_instruction() {
        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are helpful"),
                UserMessage.from("Hello"));

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
        assertThat(result.parts().get().get(0).text().get())
                .isEqualTo("First instruction\nSecond instruction");
    }

    // --- toContents ---

    @Test
    void should_filter_out_system_messages() {
        List<ChatMessage> messages = List.of(
                SystemMessage.from("System"),
                UserMessage.from("Hello"),
                AiMessage.from("Hi"));

        List<Content> result = GoogleGenAiContentMapper.toContents(messages);

        assertThat(result).hasSize(2);
    }

    // --- toContent: UserMessage ---

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
        Image image = Image.builder().base64Data(base64Data).mimeType("image/png").build();
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

    // --- toContent: AiMessage ---

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
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .name("doSomething")
                .arguments("")
                .build();
        AiMessage message = AiMessage.from(toolRequest);

        Content result = GoogleGenAiContentMapper.toContent(message);

        assertThat(result.role().get()).isEqualTo("model");
        assertThat(result.parts().get().get(0).functionCall().get().name().get())
                .isEqualTo("doSomething");
    }

    @Test
    void should_convert_ai_message_with_invalid_json_arguments() {
        ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                .name("doSomething")
                .arguments("not-valid-json")
                .build();
        AiMessage message = AiMessage.from(toolRequest);

        Content result = GoogleGenAiContentMapper.toContent(message);

        assertThat(result.role().get()).isEqualTo("model");
        assertThat(result.parts().get().get(0).functionCall().get().name().get())
                .isEqualTo("doSomething");
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

    // --- toContent: ToolExecutionResultMessage ---

    @Test
    void should_convert_tool_execution_result_message() {
        ToolExecutionResultMessage message = ToolExecutionResultMessage.from("call-1", "getWeather", "Sunny, 25C");

        Content result = GoogleGenAiContentMapper.toContent(message);

        assertThat(result.role().get()).isEqualTo("function");
        assertThat(result.parts().get().get(0).functionResponse().get().name().get())
                .isEqualTo("getWeather");
    }

    // --- toContent: Unknown message type ---

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

    // --- toChatResponse ---

    @Test
    void should_return_empty_response_when_no_candidates() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(Collections.emptyList())
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response);

        assertThat(result.aiMessage().text()).isEqualTo("Empty response");
        assertThat(result.finishReason()).isEqualTo(FinishReason.OTHER);
        assertThat(result.tokenUsage().inputTokenCount()).isEqualTo(0);
    }

    @Test
    void should_convert_text_response() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(
                        Candidate.builder()
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

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response);

        assertThat(result.aiMessage().text()).isEqualTo("Hello!");
        assertThat(result.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(result.tokenUsage().inputTokenCount()).isEqualTo(10);
        assertThat(result.tokenUsage().outputTokenCount()).isEqualTo(5);
    }

    @Test
    void should_convert_function_call_response() {
        Map<String, Object> args = new HashMap<>();
        args.put("city", "London");

        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(
                        Candidate.builder()
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

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response);

        assertThat(result.aiMessage().toolExecutionRequests()).hasSize(1);
        assertThat(result.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("getWeather");
    }

    @Test
    void should_convert_response_with_both_text_and_function_call() {
        Map<String, Object> args = new HashMap<>();
        args.put("city", "London");

        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(
                        Candidate.builder()
                                .content(Content.builder()
                                        .role("model")
                                        .parts(
                                                Part.builder().text("Let me check the weather.").build(),
                                                Part.builder()
                                                        .functionCall(FunctionCall.builder()
                                                                .name("getWeather")
                                                                .args(args)
                                                                .build())
                                                        .build())
                                        .build())
                                .build()))
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response);

        assertThat(result.aiMessage().text()).isEqualTo("Let me check the weather.");
        assertThat(result.aiMessage().toolExecutionRequests()).hasSize(1);
    }

    @Test
    void should_handle_response_with_no_content() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder().build()))
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response);

        assertThat(result.aiMessage().text()).isEmpty();
        assertThat(result.finishReason()).isEqualTo(FinishReason.STOP);
    }

    @Test
    void should_handle_response_without_usage_metadata() {
        GenerateContentResponse response = GenerateContentResponse.builder()
                .candidates(List.of(
                        Candidate.builder()
                                .content(Content.builder()
                                        .role("model")
                                        .parts(Part.builder().text("Hello").build())
                                        .build())
                                .build()))
                .build();

        ChatResponse result = GoogleGenAiContentMapper.toChatResponse(response);

        assertThat(result.tokenUsage().inputTokenCount()).isEqualTo(0);
        assertThat(result.tokenUsage().outputTokenCount()).isEqualTo(0);
    }

    // --- detectMimeType ---

    @Test
    void should_detect_image_mime_types() {
        assertThat(GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.jpg"))).isEqualTo("image/jpeg");
        assertThat(GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.png"))).isEqualTo("image/png");
        assertThat(GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.gif"))).isEqualTo("image/gif");
        assertThat(GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.webp"))).isEqualTo("image/webp");
    }

    @Test
    void should_detect_audio_mime_types() {
        assertThat(GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.mp3"))).isEqualTo("audio/mp3");
        assertThat(GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.wav"))).isEqualTo("audio/wav");
    }

    @Test
    void should_detect_video_mime_types() {
        assertThat(GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.mp4"))).isEqualTo("video/mp4");
        assertThat(GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.avi"))).isEqualTo("video/avi");
    }

    @Test
    void should_detect_document_mime_types() {
        assertThat(GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.pdf"))).isEqualTo("application/pdf");
        assertThat(GoogleGenAiContentMapper.detectMimeType(URI.create("file:///test.csv"))).isEqualTo("text/plain");
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

    // --- fromMimeTypeAndData ---

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

    // --- readBytes failure ---

    @Test
    void should_throw_when_reading_nonexistent_file() {
        Image image = Image.builder().url(URI.create("file:///nonexistent/file.png")).build();
        UserMessage message = UserMessage.from(new ImageContent(image));

        assertThatThrownBy(() -> GoogleGenAiContentMapper.toContent(message))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to read data from");
    }

    // --- multimodal content with explicit mimeType ---

    @Test
    void should_convert_user_message_with_image_url_and_explicit_mime_type(@TempDir Path tempDir) throws Exception {
        Path imgFile = tempDir.resolve("test.dat");
        Files.write(imgFile, "fake-image-data".getBytes());

        Image image = Image.builder().url(imgFile.toUri()).mimeType("image/png").build();
        UserMessage message = UserMessage.from(new ImageContent(image));

        Content result = GoogleGenAiContentMapper.toContent(message);

        assertThat(result.parts().get()).hasSize(1);
    }

    // --- user message with multiple content types ---

    @Test
    void should_convert_user_message_with_mixed_content() {
        String base64Data = Base64.getEncoder().encodeToString("fake-image".getBytes());
        Image image = Image.builder().base64Data(base64Data).mimeType("image/jpeg").build();

        UserMessage message = UserMessage.from(
                new TextContent("Describe this image"),
                new ImageContent(image));

        Content result = GoogleGenAiContentMapper.toContent(message);

        assertThat(result.parts().get()).hasSize(2);
        assertThat(result.parts().get().get(0).text().get()).isEqualTo("Describe this image");
    }
}
