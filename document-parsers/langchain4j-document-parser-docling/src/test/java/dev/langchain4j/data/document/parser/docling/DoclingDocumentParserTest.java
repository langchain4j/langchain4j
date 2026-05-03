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
        when(mockApi.convertSource(any())).thenReturn(
                ConvertDocumentResponse.builder()
                        .document(DocumentResponse.builder()
                                .markdownContent("# Parsed Content")
                                .build())
                        .build());

        DoclingDocumentParser parser = new DoclingDocumentParser(mockApi);
        Document document = parser.parse(new ByteArrayInputStream("some bytes".getBytes()));

        assertThat(document.text()).isEqualTo("# Parsed Content");
    }

    @Test
    void shouldIncludeDocumentSizeBytesInMetadata() {
        byte[] content = "document content".getBytes();
        when(mockApi.convertSource(any())).thenReturn(
                ConvertDocumentResponse.builder()
                        .document(DocumentResponse.builder()
                                .markdownContent("Parsed text")
                                .build())
                        .build());

        DoclingDocumentParser parser = new DoclingDocumentParser(mockApi);
        Document document = parser.parse(new ByteArrayInputStream(content));

        assertThat(document.metadata().getString("document_size_bytes"))
                .isEqualTo(String.valueOf(content.length));
    }

    @Test
    void shouldReturnEmptyDocumentWhenApiReturnsNullResponse() {
        when(mockApi.convertSource(any())).thenReturn(null);

        DoclingDocumentParser parser = new DoclingDocumentParser(mockApi);
        Document document = parser.parse(new ByteArrayInputStream("data".getBytes()));

        assertThat(document.text()).isEmpty();
    }

    @Test
    void shouldReturnEmptyDocumentWhenApiReturnsEmptyContent() {
        when(mockApi.convertSource(any())).thenReturn(
                ConvertDocumentResponse.builder()
                        .document(DocumentResponse.builder()
                                .markdownContent("")
                                .build())
                        .build());

        DoclingDocumentParser parser = new DoclingDocumentParser(mockApi);
        Document document = parser.parse(new ByteArrayInputStream("data".getBytes()));

        assertThat(document.text()).isEmpty();
    }

    @Test
    void shouldImplementDocumentParserInterface() {
        assertThat(new DoclingDocumentParser(mockApi))
                .isInstanceOf(dev.langchain4j.data.document.DocumentParser.class);
    }
}
