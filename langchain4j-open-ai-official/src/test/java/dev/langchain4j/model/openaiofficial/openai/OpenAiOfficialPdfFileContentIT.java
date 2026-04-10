package dev.langchain4j.model.openaiofficial.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModel;
import dev.langchain4j.model.openaiofficial.OpenAiOfficialStreamingChatModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiOfficialPdfFileContentIT {

    private static final String MODEL_NAME = "gpt-5.4-mini";

    @Test
    void should_accept_pdf_file_content_in_non_streaming_mode() throws Exception {

        OpenAiOfficialChatModel model = nonStreamingModel();
        Path samplePdf = samplePdfPath();

        UserMessage userMessage = UserMessage.builder()
                .addContent(TextContent.from("What information is in the attached PDF? Return only the extracted text."))
                .addContent(PdfFileContent.from(samplePdf))
                .build();

        ChatResponse response = model.chat(userMessage);

        assertThat(response.aiMessage().text())
                .containsIgnoringCase("Berlin")
                .containsIgnoringCase("capital")
                .containsIgnoringCase("Germany");
    }

    @Test
    void should_accept_pdf_file_content_in_streaming_mode() throws Exception {

        OpenAiOfficialStreamingChatModel model = streamingModel();
        Path samplePdf = samplePdfPath();
        String pdfBase64 = Base64.getEncoder().encodeToString(Files.readAllBytes(samplePdf));
        PdfFile pdfFile = PdfFile.builder().base64Data(pdfBase64).mimeType("application/pdf").build();

        UserMessage userMessage = UserMessage.builder()
                .addContent(TextContent.from("What information is in the attached PDF? Return only the extracted text."))
                .addContent(PdfFileContent.from(pdfFile))
                .build();

        TestStreamingChatResponseHandler handler = new TestStreamingChatResponseHandler();
        model.chat(List.of(userMessage), handler);

        assertThat(handler.get().aiMessage().text())
                .containsIgnoringCase("Berlin")
                .containsIgnoringCase("capital")
                .containsIgnoringCase("Germany");
    }

    private static Path samplePdfPath() throws Exception {
        return Paths.get(OpenAiOfficialPdfFileContentIT.class
                .getClassLoader()
                .getResource("sample.pdf")
                .toURI());
    }

    private static OpenAiOfficialChatModel nonStreamingModel() {
        OpenAiOfficialChatModel.Builder builder = OpenAiOfficialChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(MODEL_NAME)
                .temperature(0.0);

        String baseUrl = System.getenv("OPENAI_BASE_URL");
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }

        String organizationId = System.getenv("OPENAI_ORGANIZATION_ID");
        if (organizationId != null && !organizationId.isBlank()) {
            builder.organizationId(organizationId);
        }

        return builder.build();
    }

    private static OpenAiOfficialStreamingChatModel streamingModel() {
        OpenAiOfficialStreamingChatModel.Builder builder = OpenAiOfficialStreamingChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName(MODEL_NAME)
                .temperature(0.0);

        String baseUrl = System.getenv("OPENAI_BASE_URL");
        if (baseUrl != null && !baseUrl.isBlank()) {
            builder.baseUrl(baseUrl);
        }

        String organizationId = System.getenv("OPENAI_ORGANIZATION_ID");
        if (organizationId != null && !organizationId.isBlank()) {
            builder.organizationId(organizationId);
        }

        return builder.build();
    }
}
