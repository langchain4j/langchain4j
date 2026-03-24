package dev.langchain4j.model.openaiofficial;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.pdf.PdfFile;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;

class InternalOpenAiOfficialHelperTest {

    @Test
    void should_convert_pdf_file_content_with_base64_to_openai_file_part() {

        UserMessage userMessage = UserMessage.from(
                TextContent.from("Analyze the attached document."),
                PdfFileContent.from(PdfFile.builder().base64Data("YWJj").mimeType("application/pdf").build()));

        ChatCompletionMessageParam messageParam = InternalOpenAiOfficialHelper.toOpenAiMessage(userMessage);

        assertThat(messageParam.isUser()).isTrue();
        List<ChatCompletionContentPart> parts = messageParam.asUser().content().asArrayOfContentParts();
        assertThat(parts).hasSize(2);

        ChatCompletionContentPart filePart = parts.get(1);
        assertThat(filePart.isFile()).isTrue();
        assertThat(filePart.asFile().file().fileData()).hasValue("data:application/pdf;base64,YWJj");
        assertThat(filePart.asFile().file().filename()).hasValue("pdf_file");
    }

    @Test
    void should_convert_pdf_file_content_with_url_to_openai_file_part() {

        String url = "https://example.com/test.pdf";
        UserMessage userMessage = UserMessage.from(
                TextContent.from("Analyze the attached document."),
                PdfFileContent.from(PdfFile.builder().url(URI.create(url)).build()));

        ChatCompletionMessageParam messageParam = InternalOpenAiOfficialHelper.toOpenAiMessage(userMessage);

        assertThat(messageParam.isUser()).isTrue();
        List<ChatCompletionContentPart> parts = messageParam.asUser().content().asArrayOfContentParts();
        assertThat(parts).hasSize(2);

        ChatCompletionContentPart filePart = parts.get(1);
        assertThat(filePart.isFile()).isTrue();
        assertThat(filePart.asFile().file().fileData()).hasValue(url);
        assertThat(filePart.asFile().file().filename()).hasValue("pdf_file");
    }
}
