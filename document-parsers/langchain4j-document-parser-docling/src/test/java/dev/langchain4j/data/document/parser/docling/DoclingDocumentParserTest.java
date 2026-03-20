package dev.langchain4j.data.document.parser.docling;

import dev.langchain4j.data.document.Document;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DoclingDocumentParserTest {
    class DoclingDocumentParserTest {
    
    @Test
    void shouldThrowExceptionWhenInputStreamIsNull() {
        // Given
        DoclingDocumentParser parser = new DoclingDocumentParser("http://localhost:5001");

        // When/Then
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be null");
    }
}
}