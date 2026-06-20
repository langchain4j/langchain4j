package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
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

        assertThat(message.toString()).doesNotContain(secret);
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
}
