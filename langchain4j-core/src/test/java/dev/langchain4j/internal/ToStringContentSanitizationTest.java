package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.audio.Audio;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.video.Video;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import org.junit.jupiter.api.Test;

class ToStringContentSanitizationTest {

    @Test
    void document_toString_shouldNotContainRawText() {
        String secret = "my-secret-document-content-that-must-not-leak-to-logs";
        Document document = Document.from(secret);

        assertThat(document.toString()).doesNotContain(secret).contains("[length=" + secret.length() + "]");
    }

    @Test
    void textSegment_toString_shouldNotContainRawText() {
        String secret = "chunk-with-sensitive-data";
        TextSegment segment = TextSegment.from(secret);

        assertThat(segment.toString()).doesNotContain(secret).contains("[length=" + secret.length() + "]");
    }

    @Test
    void textContent_toString_shouldNotContainRawText() {
        String secret = "user prompt with sensitive data";
        TextContent content = TextContent.from(secret);

        assertThat(content.toString()).doesNotContain(secret).contains("[length=" + secret.length() + "]");
    }

    @Test
    void userMessage_toString_shouldNotContainRawText() {
        String secret = "user prompt with sensitive data";
        UserMessage message = UserMessage.from(secret);

        assertThat(message.toString()).doesNotContain(secret).contains("[length=" + secret.length() + "]");
    }

    @Test
    void systemMessage_toString_shouldNotContainRawText() {
        String secret = "system instructions with sensitive data";
        SystemMessage message = SystemMessage.from(secret);

        assertThat(message.toString()).doesNotContain(secret).contains("[length=" + secret.length() + "]");
    }

    @Test
    void aiMessage_toString_shouldNotContainRawTextOrThinking() {
        String secret = "assistant response with sensitive data";
        String thinking = "internal reasoning that should stay private";
        AiMessage message = AiMessage.builder().text(secret).thinking(thinking).build();

        assertThat(message.toString())
                .doesNotContain(secret)
                .doesNotContain(thinking)
                .contains("[length=" + secret.length() + "]")
                .contains("[length=" + thinking.length() + "]");
    }

    @Test
    void prompt_toString_shouldNotContainRawText() {
        String secret = "prompt template result with sensitive data";
        Prompt prompt = Prompt.from(secret);

        assertThat(prompt.toString()).doesNotContain(secret).contains("[length=" + secret.length() + "]");
    }

    @Test
    void image_toString_shouldNotContainBase64DataOrRevisedPrompt() {
        String base64 = "a".repeat(10_000);
        String revisedPrompt = "generated image prompt with sensitive data";
        Image image = Image.builder()
                .base64Data(base64)
                .mimeType("image/png")
                .revisedPrompt(revisedPrompt)
                .build();

        assertThat(image.toString())
                .doesNotContain(base64)
                .doesNotContain(revisedPrompt)
                .contains("[length=10000]")
                .contains("[length=" + revisedPrompt.length() + "]");
    }

    @Test
    void audio_toString_shouldNotContainBase64Data() {
        String base64 = "a".repeat(5_000);
        Audio audio = Audio.builder().base64Data(base64).mimeType("audio/mp3").build();

        assertThat(audio.toString()).doesNotContain(base64).contains("[length=5000]");
    }

    @Test
    void pdfFile_toString_shouldNotContainBase64Data() {
        String base64 = "a".repeat(8_000);
        PdfFile pdfFile =
                PdfFile.builder().base64Data(base64).mimeType("application/pdf").build();

        assertThat(pdfFile.toString()).doesNotContain(base64).contains("[length=8000]");
    }

    @Test
    void video_toString_shouldNotContainBase64Data() {
        String base64 = "a".repeat(12_000);
        Video video = Video.builder().base64Data(base64).mimeType("video/mp4").build();

        assertThat(video.toString()).doesNotContain(base64).contains("[length=12000]");
    }

    @Test
    void query_toString_shouldNotContainRawText() {
        String secret = "rag query with sensitive data";
        Query query = Query.from(secret);

        assertThat(query.toString()).doesNotContain(secret).contains("[length=" + secret.length() + "]");
    }

    @Test
    void content_toString_shouldNotContainRawText() {
        String secret = "retrieved content with sensitive data";
        Content content = Content.from(secret);

        assertThat(content.toString()).doesNotContain(secret);
    }

    @Test
    void moderation_toString_shouldNotContainFlaggedText() {
        String secret = "flagged content with sensitive data";
        Moderation moderation = Moderation.flagged(secret);

        assertThat(moderation.toString()).doesNotContain(secret).contains("[length=" + secret.length() + "]");
    }

    @Test
    void toolExecutionRequest_toString_shouldNotContainArguments() {
        String secret = "{\"password\":\"super-secret\"}";
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("id")
                .name("tool")
                .arguments(secret)
                .build();

        assertThat(request.toString()).doesNotContain(secret).contains("[length=" + secret.length() + "]");
    }

    @Test
    void partialToolCall_toString_shouldNotContainPartialArguments() {
        String secret = "{\"token\":\"secret-value\"}";
        PartialToolCall partialToolCall = PartialToolCall.builder()
                .index(0)
                .id("id")
                .name("tool")
                .partialArguments(secret)
                .build();

        assertThat(partialToolCall.toString()).doesNotContain(secret).contains("[length=" + secret.length() + "]");
    }

    @Test
    void partialThinking_toString_shouldNotContainRawText() {
        String secret = "streaming thinking with sensitive data";
        PartialThinking partialThinking = new PartialThinking(secret);

        assertThat(partialThinking.toString()).doesNotContain(secret).contains("[length=" + secret.length() + "]");
    }

    @Test
    void partialResponse_toString_shouldNotContainRawText() {
        String secret = "streaming response with sensitive data";
        PartialResponse partialResponse = new PartialResponse(secret);

        assertThat(partialResponse.toString()).doesNotContain(secret).contains("[length=" + secret.length() + "]");
    }

    @Test
    void jsonRawSchema_toString_shouldNotContainRawSchema() {
        String secret = "{\"type\":\"object\",\"properties\":{\"password\":{\"type\":\"string\"}}}";
        JsonRawSchema jsonRawSchema = JsonRawSchema.from(secret);

        assertThat(jsonRawSchema.toString()).doesNotContain(secret).contains("[length=" + secret.length() + "]");
    }
}
