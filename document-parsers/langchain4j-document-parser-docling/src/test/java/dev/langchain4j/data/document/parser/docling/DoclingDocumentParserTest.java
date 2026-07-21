package dev.langchain4j.data.document.parser.docling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.convert.response.DocumentResponse;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DoclingDocumentParserTest {

    @Mock
    private DoclingServeApi mockApi;

    @Test
    void shouldThrowWhenInputStreamIsNull() {
        DoclingDocumentParser parser = new DoclingDocumentParser(mockApi);

        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }

    @Test
    void shouldThrowWhenInputStreamIsEmpty() {
        DoclingDocumentParser parser = new DoclingDocumentParser(mockApi);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(BlankDocumentException.class);
    }

    @Test
    void shouldThrowWhenApiInstanceIsNull() {
        assertThatThrownBy(() -> new DoclingDocumentParser(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnDocumentWithParsedText() {
        mockResponseWithMarkdown("# Parsed Content");

        DoclingDocumentParser parser = new DoclingDocumentParser(mockApi);
        Document document = parser.parse(new ByteArrayInputStream("some bytes".getBytes()));

        assertThat(document.text()).isEqualTo("# Parsed Content");
    }

    @Test
    void shouldIncludeDocumentSizeBytesInMetadata() {
        byte[] content = "document content".getBytes();
        mockResponseWithMarkdown("Parsed text");

        DoclingDocumentParser parser = new DoclingDocumentParser(mockApi);
        Document document = parser.parse(new ByteArrayInputStream(content));

        assertThat(document.metadata().getString("document_size_bytes")).isEqualTo(String.valueOf(content.length));
    }

    @Test
    void shouldHandleEmptyDocumentWhenApiReturnsEmptyContent() {
        mockResponseWithMarkdown("");

        assertThatThrownBy(() -> new DoclingDocumentParser(mockApi).parse(new ByteArrayInputStream("data".getBytes())))
                .isInstanceOf(BlankDocumentException.class);
    }

    @Test
    void shouldImplementDocumentParserInterface() {
        assertThat(new DoclingDocumentParser(mockApi)).isInstanceOf(DocumentParser.class);
    }

    @Test
    void shouldReturnDocumentWithParsedText_usingBuilder() {
        mockResponseWithMarkdown("# Parsed Content");

        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .build();
        var document = parser.parse(new ByteArrayInputStream("some bytes".getBytes()));

        assertThat(document.text()).isEqualTo("# Parsed Content");
    }

    @Test
    void shouldThrowWhenDoclingClientIsNull_usingBuilder() {
        assertThatThrownBy(() -> DoclingDocumentParser.builder().build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldUseCustomDocumentTextExtractor() {
        mockResponseWith("# Markdown", "<h1>HTML</h1>", null, null);

        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .documentTextExtractor(response -> response.getDocument().getHtmlContent())
                .build();
        var document = parser.parse(new ByteArrayInputStream("some bytes".getBytes()));

        assertThat(document.text()).isEqualTo("<h1>HTML</h1>");
    }

    @Test
    void shouldUseDefaultMarkdownExtractorWhenNoneSpecified() {
        mockResponseWith("# Markdown", "<h1>HTML</h1>", "Plain text", null);

        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .build();
        var document = parser.parse(new ByteArrayInputStream("some bytes".getBytes()));

        assertThat(document.text()).isEqualTo("# Markdown");
    }

    @Test
    void shouldThrowBlankDocumentExceptionWhenExtractorReturnsNull() {
        mockResponseWithMarkdown("# Markdown");

        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .documentTextExtractor(response -> null)
                .build();

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream("data".getBytes())))
                .isInstanceOf(BlankDocumentException.class);
    }

    @Test
    void shouldThrowBlankDocumentExceptionWhenExtractorReturnsBlank() {
        mockResponseWithMarkdown("# Markdown");

        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .documentTextExtractor(response -> "   ")
                .build();

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream("data".getBytes())))
                .isInstanceOf(BlankDocumentException.class);
    }

    @Test
    void shouldAllowAccessToFullResponse() {
        when(mockApi.convertSource(any(ConvertDocumentRequest.class)))
                .thenReturn(InBodyConvertDocumentResponse.builder()
                        .document(DocumentResponse.builder()
                                .markdownContent("content")
                                .build())
                        .status("SUCCESS")
                        .processingTime(1.5)
                        .build());

        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .documentTextExtractor(response ->
                        "%s (status=%s, time=%.1f)".formatted(
                                response.getDocument().getMarkdownContent(),
                                response.getStatus(),
                                response.getProcessingTime()))
                .build();
        var document = parser.parse(new ByteArrayInputStream("data".getBytes()));

        assertThat(document.text()).isEqualTo("content (status=SUCCESS, time=1.5)");
    }

    @Test
    void shouldBuildWithOptions() {
        mockResponseWithMarkdown("# Content");

        var options = ConvertDocumentOptions.builder().build();
        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .options(options)
                .build();
        var document = parser.parse(new ByteArrayInputStream("data".getBytes()));

        assertThat(document.text()).isEqualTo("# Content");
    }

    @Test
    void shouldExtractTextContent() {
        mockResponseWith(null, null, "Plain text content", null);

        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .documentTextExtractor(response -> response.getDocument().getTextContent())
                .build();
        var document = parser.parse(new ByteArrayInputStream("data".getBytes()));

        assertThat(document.text()).isEqualTo("Plain text content");
    }

    @Test
    void shouldExtractDoctagsContent() {
        mockResponseWith(null, null, null, "<doctag>content</doctag>");

        var parser = DoclingDocumentParser.builder()
                .doclingClient(mockApi)
                .documentTextExtractor(response -> response.getDocument().getDoctagsContent())
                .build();
        var document = parser.parse(new ByteArrayInputStream("data".getBytes()));

        assertThat(document.text()).isEqualTo("<doctag>content</doctag>");
    }

    private void mockResponseWithMarkdown(String markdown) {
        mockResponseWith(markdown, null, null, null);
    }

    private void mockResponseWith(String markdown, String html, String text, String doctags) {
        when(mockApi.convertSource(any(ConvertDocumentRequest.class)))
                .thenReturn(InBodyConvertDocumentResponse.builder()
                        .document(DocumentResponse.builder()
                                .markdownContent(markdown)
                                .htmlContent(html)
                                .textContent(text)
                                .doctagsContent(doctags)
                                .build())
                        .build());
    }
}
