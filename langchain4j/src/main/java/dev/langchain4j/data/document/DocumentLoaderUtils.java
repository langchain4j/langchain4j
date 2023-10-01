package dev.langchain4j.data.document;

import dev.langchain4j.data.document.parser.MsOfficeDocumentParser;
import dev.langchain4j.data.document.parser.PdfDocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;

import java.io.InputStream;

class DocumentLoaderUtils {

    static Document load(DocumentSource source, DocumentParser parser) {
        try (InputStream inputStream = source.inputStream()) {
            Document document = parser.parse(inputStream);
            source.metadata().asMap().forEach((key, value) -> document.metadata().add(key, value));
            return document;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load document", e);
        }
    }

    static DocumentParser parserFor(DocumentType type) {
        switch (type) {
            case TXT:
            case HTML:
            case UNKNOWN:
                return new TextDocumentParser(type);
            case PDF:
                return new PdfDocumentParser();
            case DOC:
            case XLS:
            case PPT:
                return new MsOfficeDocumentParser(type);
            default:
                throw new RuntimeException(String.format("Cannot find parser for document type '%s'", type));
        }
    }
}
