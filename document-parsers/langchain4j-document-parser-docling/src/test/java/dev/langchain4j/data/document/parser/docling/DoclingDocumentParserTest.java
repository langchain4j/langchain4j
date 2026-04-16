package dev.langchain4j.data.document.parser.docling;

import ai.docling.api.serve.DoclingServeApi;
import ai.docling.api.serve.convert.response.ConvertDocumentResponse;
import ai.docling.api.serve.convert.response.DocumentResponse;
import dev.langchain4j.data.document.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoclingDocumentParserTest {

    @Mock
    private DoclingServeApi mockApi;

    @Mock
    private ConvertDocumentResponse mockResponse;

    @Mock
    private DocumentResponse mockDocumentResponse;

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
        InputStream emptyStream = new ByteArrayInputStream(new byte[0]);

        assertThatThrownBy(() -> parser.parse(emptyStream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void shouldThrowWhenApiInstanceIsNull() {
        assertThatThrownBy(() -> new DoclingDocumentParser((DoclingServeApi) null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnDocumentWithParsedText() {
        when(mockApi.convertSource(any())).thenReturn(mockResponse);
        when(mockResponse.getDocument()).thenReturn(mockDocumentResponse);
        when(mockResponse.getErrors()).thenReturn(null);
        when(mockDocumentResponse.getMarkdownContent()).thenReturn("# Parsed Content");

        DoclingDocumentParser parser = new DoclingDocumentParser(mockApi);
        Document document = parser.parse(new ByteArrayInputStream("some bytes".getBytes()));

        assertThat(document.text()).isEqualTo("# Parsed Content");
    }

    @Test
    void shouldIncludeDocumentSizeBytesInMetadata() {
        byte[] content = "document content".getBytes();
        when(mockApi.convertSource(any())).thenReturn(mockResponse);
        when(mockResponse.getDocument()).thenReturn(mockDocumentResponse);
        when(mockResponse.getErrors()).thenReturn(null);
        when(mockDocumentResponse.getMarkdownContent()).thenReturn("Parsed text");

        DoclingDocumentParser parser = new DoclingDocumentParser(mockApi);
        Document document = parser.parse(new ByteArrayInputStream(content));

        assertThat(document.metadata().getString("document_size_bytes"))
                .isEqualTo(String.valueOf(content.length));
    }

    @Test
    void shouldThrowWhenApiReturnsNullResponse() {
        when(mockApi.convertSource(any())).thenReturn(null);

        DoclingDocumentParser parser = new DoclingDocumentParser(mockApi);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream("data".getBytes())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("empty response");
    }

    @Test
    void shouldThrowWhenApiReturnsEmptyContent() {
        when(mockApi.convertSource(any())).thenReturn(mockResponse);
        when(mockResponse.getDocument()).thenReturn(mockDocumentResponse);
        when(mockResponse.getErrors()).thenReturn(null);
        when(mockDocumentResponse.getMarkdownContent()).thenReturn("");

        DoclingDocumentParser parser = new DoclingDocumentParser(mockApi);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream("data".getBytes())))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("no text content");
    }

    @Test
    void shouldImplementDocumentParserInterface() {
        DoclingDocumentParser parser = new DoclingDocumentParser(mockApi);
        assertThat(parser).isInstanceOf(dev.langchain4j.data.document.DocumentParser.class);
    }
}
