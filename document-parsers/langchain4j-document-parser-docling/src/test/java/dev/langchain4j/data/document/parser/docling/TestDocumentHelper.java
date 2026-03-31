package dev.langchain4j.data.document.parser.docling;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Helper utilities for creating test documents.
 * 
 * Provides methods to generate minimal valid documents for testing purposes.
 */
class TestDocumentHelper {

    /**
     * Creates a minimal valid PDF document for testing.
     * 
     * This is a simplified PDF structure - just enough to be recognized as valid.
     * In production, use actual sample PDFs from test resources.
     * 
     * @return byte array containing minimal PDF content
     */
    static byte[] createMinimalPdf() {
        // TODO: Replace with actual sample PDF from test resources
        // For now, return a minimal PDF header
        String minimalPdf = "%PDF-1.4\n" +
                "1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n" +
                "2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n" +
                "3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]/Contents 4 0 R>>endobj\n" +
                "4 0 obj<</Length 44>>stream\n" +
                "BT /F1 12 Tf 100 700 Td (Test Document) Tj ET\n" +
                "endstream endobj\n" +
                "xref\n0 5\n0000000000 65535 f\n0000000009 00000 n\n0000000058 00000 n\n" +
                "0000000115 00000 n\n0000000214 00000 n\ntrailer<</Size 5/Root 1 0 R>>\n" +
                "startxref\n308\n%%EOF";
        return minimalPdf.getBytes();
    }

    /**
     * Creates test content for a DOCX document.
     * 
     * @return byte array that will be used for DOCX testing
     */
    static byte[] createMinimalDocx() {
        // TODO: Implement minimal DOCX creation or load from resources
        // DOCX is a ZIP file containing XML - more complex than PDF
        return new byte[0];
    }

    /**
     * Creates simple text content for testing.
     * 
     * @return byte array containing plain text
     */
    static byte[] createSimpleTextDocument() {
        String text = "This is a test document.\n" +
                     "It contains multiple lines.\n" +
                     "Used for integration testing.";
        return text.getBytes();
    }
}