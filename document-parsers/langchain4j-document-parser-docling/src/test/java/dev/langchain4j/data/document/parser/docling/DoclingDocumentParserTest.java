package dev.langchain4j.data.document.parser.docling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
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
        when(mockApi.convertSource(any(ConvertDocumentRequest.class)))
                .thenReturn(InBodyConvertDocumentResponse.builder()
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
        when(mockApi.convertSource(any(ConvertDocumentRequest.class)))
                .thenReturn(InBodyConvertDocumentResponse.builder()
                        .document(DocumentResponse.builder()
                                .markdownContent("Parsed text")
                                .build())
                        .build());

        DoclingDocumentParser parser = new DoclingDocumentParser(mockApi);
        Document document = parser.parse(new ByteArrayInputStream(content));

        assertThat(document.metadata().getString("document_size_bytes")).isEqualTo(String.valueOf(content.length));
    }

    @Test
    void shouldHandleEmptyDocumentWhenApiReturnsEmptyContent() {
        when(mockApi.convertSource(any(ConvertDocumentRequest.class)))
                .thenReturn(InBodyConvertDocumentResponse.builder()
                        .document(DocumentResponse.builder().markdownContent("").build())
                        .build());

        assertThatThrownBy(() -> new DoclingDocumentParser(mockApi).parse(new ByteArrayInputStream("data".getBytes())))
                .isInstanceOf(BlankDocumentException.class);
    }

    @Test
    void shouldImplementDocumentParserInterface() {
        assertThat(new DoclingDocumentParser(mockApi)).isInstanceOf(DocumentParser.class);
    }
}
