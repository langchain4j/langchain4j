package dev.langchain4j.data.document.parser.docling;

import dev.langchain4j.data.document.Document;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DoclingDocumentParser.
 * 
 * These tests require a running docling-serve instance and are disabled by default.
 * To run these tests:
 * 1. Start docling-serve: `docling-serve dev`
 * 2. Remove @Disabled annotations
 * 3. Run tests
 * 
 * Future enhancement: Use Testcontainers to automatically start docling-serve
 */
class DoclingDocumentParserIntegrationTest {

    private static final String DOCLING_URL = "http://localhost:5001";

    @Test
    @Disabled("Requires running docling-serve instance")
    void shouldParsePdfDocument() {
        // Given
        DoclingDocumentParser parser = new DoclingDocumentParser(DOCLING_URL);
        // TODO: Add sample PDF bytes or load from resources
        byte[] samplePdf = createSamplePdfBytes();
        InputStream inputStream = new ByteArrayInputStream(samplePdf);

        // When
        Document document = parser.parse(inputStream);

        // Then
        assertThat(document).isNotNull();
        assertThat(document.text()).isNotEmpty();
        assertThat(document.metadata().getString("parser")).isEqualTo("Docling");
        assertThat(document.metadata().getString("document_size_bytes")).isNotNull();
    }

    @Test
    @Disabled("Requires running docling-serve instance")
    void shouldParseDocxDocument() {
        // Given
        DoclingDocumentParser parser = new DoclingDocumentParser(DOCLING_URL);
        // TODO: Add sample DOCX bytes or load from resources
        byte[] sampleDocx = createSampleDocxBytes();
        InputStream inputStream = new ByteArrayInputStream(sampleDocx);

        // When
        Document document = parser.parse(inputStream);

        // Then
        assertThat(document).isNotNull();
        assertThat(document.text()).isNotEmpty();
        assertThat(document.metadata().getString("parser")).isEqualTo("Docling");
    }

    @Test
    @Disabled("Requires running docling-serve instance")
    void shouldExtractMetadataFromDocument() {
        // Given
        DoclingDocumentParser parser = new DoclingDocumentParser(DOCLING_URL);
        byte[] samplePdf = createSamplePdfBytes();
        InputStream inputStream = new ByteArrayInputStream(samplePdf);

        // When
        Document document = parser.parse(inputStream);

        // Then - verify all expected metadata is present
        assertThat(document.metadata().getString("parser")).isEqualTo("Docling");
        assertThat(document.metadata().getString("document_size_bytes")).isNotNull();
        assertThat(document.metadata().getString("docling_processing_time_ms")).isNotNull();
        assertThat(document.metadata().getString("timeout_seconds")).isEqualTo("60");
    }

    @Test
    @Disabled("Requires running docling-serve instance")
    void shouldHandleCustomTimeout() {
        // Given - parser with custom 120 second timeout
        DoclingDocumentParser parser = new DoclingDocumentParser(DOCLING_URL, 120);
        byte[] samplePdf = createSamplePdfBytes();
        InputStream inputStream = new ByteArrayInputStream(samplePdf);

        // When
        Document document = parser.parse(inputStream);

        // Then
        assertThat(document.metadata().getString("timeout_seconds")).isEqualTo("120");
    }

    @Test
    @Disabled("Requires running docling-serve instance and testcontainers setup")
    void shouldWorkWithTestcontainers() {
        // TODO: Implement testcontainers-based test
        // This will automatically start docling-serve in a Docker container
        // See: https://github.com/DS4SD/docling-java for testcontainers examples
    }

    // Helper methods - to be implemented with actual sample documents
    private byte[] createSamplePdfBytes() {
        return TestDocumentHelper.createMinimalPdf();
    }

    private byte[] createSampleDocxBytes() {
        return TestDocumentHelper.createMinimalDocx();
    }
}