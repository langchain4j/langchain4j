package dev.langchain4j.data.document.parser;

import java.io.IOException;
import java.io.InputStream;

import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentType;
import dev.langchain4j.data.document.Metadata;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Extracts text from a Microsoft Office document.
 * This parser supports various file formats, including ppt, pptx, doc, docx, xls, and xlsx.
 * For detailed information on supported formats, please refer to the official Apache POI website:
 * {@link <a href="https://poi.apache.org/">https://poi.apache.org/</a>}
 */
public class MsOfficeDocumentParser implements DocumentParser {

    private final DocumentType documentType;

    public MsOfficeDocumentParser(DocumentType documentType) {
        this.documentType = ensureNotNull(documentType, "documentType");
    }

    @Override
    public Document parse(InputStream inputStream) {
        try (POITextExtractor extractor = ExtractorFactory.createExtractor(inputStream)) {
            String text = extractor.getText();
            
            return new Document(text, new Metadata().add(DOCUMENT_TYPE, this.documentType));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
}
