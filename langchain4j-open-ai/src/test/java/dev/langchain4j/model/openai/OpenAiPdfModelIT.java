package dev.langchain4j.model.openai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiPdfModelIT {

    // This is a minimal PDF with text "The capital of Germany is Berlin."
    private static final String PDF_MIME_TYPE = "application/pdf";

    ChatModel model = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName("gpt-4o-mini")
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_extract_information_from_pdf() throws URISyntaxException, IOException {

        Path file =
                Paths.get(getClass().getClassLoader().getResource("Sample PDF Export.pdf").toURI());
        String pdfBase64 = Base64.getEncoder().encodeToString(Files.readAllBytes(file));
        // Create a PDF file
        PdfFile pdfFile = PdfFile.builder()
                .base64Data(pdfBase64)
                .mimeType(PDF_MIME_TYPE)
                .build();

        // Create a user message with text and PDF content
        UserMessage userMessage = UserMessage.builder()
                .addContent(TextContent.from("What information is in the attached PDF? Return only the exact text."))
                .addContent(PdfFileContent.from(pdfFile))
                .build();

        // Generate a response
        ChatResponse response = model.chat(userMessage);
        AiMessage aiMessage = response.aiMessage();

        // Verify the response
        assertThat(aiMessage.text()).containsIgnoringCase("Berlin")
                .containsIgnoringCase("capital")
                .containsIgnoringCase("Germany");

        // Verify token usage
        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isPositive();
        assertThat(tokenUsage.outputTokenCount()).isPositive();
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        // Verify finish reason
        assertThat(response.finishReason()).isEqualTo(STOP);
    }
}
