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
    @Test
    void shouldAllowMultipleParserInstances() {
        // When
        DoclingDocumentParser parser1 = new DoclingDocumentParser();
        DoclingDocumentParser parser2 = new DoclingDocumentParser("http://localhost:5001");

        // Then - verify both instances are created and independent
        assertThat(parser1).isNotNull();
        assertThat(parser2).isNotNull();
        assertThat(parser1).isNotSameAs(parser2);
    }
    @Test
    void shouldAcceptValidHttpUrl() {
        // Given
        String httpUrl = "http://docling-server:5001";

        // When/Then - should not throw
        DoclingDocumentParser parser = new DoclingDocumentParser(httpUrl);
        assertThat(parser).isNotNull();
    }
    @Test
    void shouldAcceptLocalhostUrl() {
        // Given
        String localhostUrl = "http://localhost:5001";

        // When/Then - should not throw
        DoclingDocumentParser parser = new DoclingDocumentParser(localhostUrl);
        assertThat(parser).isNotNull();
    }

    @Test
    void shouldAcceptIpAddressUrl() {
        // Given
        String ipUrl = "http://192.168.1.100:5001";

        // When/Then - should not throw
        DoclingDocumentParser parser = new DoclingDocumentParser(ipUrl);
        assertThat(parser).isNotNull();
    }

    @Test
    void shouldAcceptUrlWithoutPort() {
        // Given
        String urlWithoutPort = "http://docling-server";

        // When/Then - should not throw
        DoclingDocumentParser parser = new DoclingDocumentParser(urlWithoutPort);
        assertThat(parser).isNotNull();
    }

    @Test
    void shouldAcceptUrlWithDifferentPort() {
        // Given
        String customPortUrl = "http://localhost:8080";

        // When/Then - should not throw
        DoclingDocumentParser parser = new DoclingDocumentParser(customPortUrl);
        assertThat(parser).isNotNull();
    }

    @Test
    void shouldThrowExceptionForWhitespaceOnlyServerUrl() {
        // When/Then
        assertThatThrownBy(() -> new DoclingDocumentParser("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("server URL");
    }

    @Test
    void shouldHandleInputStreamWithContent() {
        // Given
        DoclingDocumentParser parser = new DoclingDocumentParser("http://localhost:5001");
        byte[] sampleData = "Sample PDF content".getBytes();
        InputStream inputStream = new ByteArrayInputStream(sampleData);

        // When/Then - we can't test full parsing without server, but we can verify it doesn't crash on valid input
        // This will throw an exception about server connectivity, which is expected
        assertThatThrownBy(() -> parser.parse(inputStream))
                .isInstanceOf(RuntimeException.class);
    }
}