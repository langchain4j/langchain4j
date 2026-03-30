package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.pdf.PdfFile;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiResponsesClientPdfSupportTest {

    @Test
    void should_map_pdf_file_content_to_input_file() throws Exception {

        // given
        String pdfBase64 = samplePdfBase64();
        UserMessage userMessage = UserMessage.builder()
                .addContent(TextContent.from("What information is in the attached PDF?"))
                .addContent(PdfFileContent.from(PdfFile.builder()
                        .base64Data(pdfBase64)
                        .mimeType("application/pdf")
                        .build()))
                .build();

        OpenAiResponsesClient client =
                OpenAiResponsesClient.builder().apiKey("test-api-key").build();
        Method toResponsesMessages =
                OpenAiResponsesClient.class.getDeclaredMethod("toResponsesMessages", ChatMessage.class);
        toResponsesMessages.setAccessible(true);

        // when
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages =
                (List<Map<String, Object>>) toResponsesMessages.invoke(client, userMessage);

        // then
        assertThat(messages).hasSize(1);
        Map<String, Object> message = messages.get(0);
        assertThat(message).containsEntry("type", "message").containsEntry("role", "user");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) message.get("content");
        assertThat(content).hasSize(2);

        Map<String, Object> pdfInput = content.stream()
                .filter(contentItem -> "input_file".equals(contentItem.get("type")))
                .findFirst()
                .orElseThrow();

        assertThat(pdfInput).containsEntry("filename", "pdf_file");
        assertThat((String) pdfInput.get("file_data")).isEqualTo("data:application/pdf;base64," + pdfBase64);
    }

    private String samplePdfBase64() throws Exception {
        URL samplePdf = getClass().getClassLoader().getResource("sample.pdf");
        assertThat(samplePdf).isNotNull();
        byte[] pdfBytes = Files.readAllBytes(Path.of(samplePdf.toURI()));
        return Base64.getEncoder().encodeToString(pdfBytes);
    }
}
