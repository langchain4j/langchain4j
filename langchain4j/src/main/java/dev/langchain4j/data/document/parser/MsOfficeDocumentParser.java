package dev.langchain4j.data.document.parser;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentType;
import dev.langchain4j.data.document.Metadata;
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;

import java.io.IOException;
import java.io.InputStream;

import static dev.langchain4j.data.document.Document.DOCUMENT_TYPE;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Extracts text from a Microsoft Office document.
 * This parser supports various file formats, including ppt, pptx, doc, docx, xls, and xlsx.
 * For detailed information on supported formats, please refer to the <a href="https://poi.apache.org/">official Apache POI website</a>.
 */
public class MsOfficeDocumentParser implements DocumentParser {

    private final DocumentType documentType;

    public MsOfficeDocumentParser(DocumentType documentType) {
        this.documentType = ensureNotNull(documentType, "documentType");
    }

    @Override
    public Document parse(InputStream inputStream) {
        try (POITextExtractor extractor = ExtractorFactory.createExtractor(inputStream)) {
            return new Document(extractor.getText(), Metadata.from(DOCUMENT_TYPE, documentType));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}