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
    @Test
    void shouldThrowExceptionWhenInputStreamIsEmpty() {
        // Given
        DoclingDocumentParser parser = new DoclingDocumentParser("http://localhost:5001");
        InputStream emptyStream = new ByteArrayInputStream(new byte[0]);

        // When/Then
        assertThatThrownBy(() -> parser.parse(emptyStream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty");
    }
    @Test
    void shouldThrowExceptionWhenServerUrlIsNull() {
        // When/Then
        assertThatThrownBy(() -> new DoclingDocumentParser(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("server URL");
    }
    @Test
    void shouldThrowExceptionWhenServerUrlIsEmpty() {
        // When/Then
        assertThatThrownBy(() -> new DoclingDocumentParser(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("server URL");
    }
    @Test
    void shouldUseDefaultServerUrlWhenNoUrlProvided() {
        // When
        DoclingDocumentParser parser = new DoclingDocumentParser();

        // Then - verifies constructor doesn't throw and creates valid instance
        assertThat(parser).isNotNull();
    }
    @Test
    void shouldImplementDocumentParserInterface() {
        // Given
        DoclingDocumentParser parser = new DoclingDocumentParser();

        // Then - verify it implements the required interface
        assertThat(parser).isInstanceOf(dev.langchain4j.data.document.DocumentParser.class);
    }
    @Test
    void shouldAcceptCustomServerUrl() {
        // Given
        String customUrl = "http://my-docling-server:8080";

        // When
        DoclingDocumentParser parser = new DoclingDocumentParser(customUrl);

        // Then - verify parser was created successfully
        assertThat(parser).isNotNull();
    }
}