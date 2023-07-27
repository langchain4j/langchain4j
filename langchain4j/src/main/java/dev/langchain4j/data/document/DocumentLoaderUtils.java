package dev.langchain4j.data.document;

import dev.langchain4j.data.document.parser.PdfDocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;

import java.io.InputStream;

import static dev.langchain4j.data.document.DocumentType.*;

class DocumentLoaderUtils {

    static Document load(DocumentSource source, DocumentParser parser) {
        try (InputStream inputStream = source.inputStream()) {
            Document document = parser.parse(inputStream);
            Metadata sourceMetadata = source.sourceMetadata();
            document.metadata().mergeFrom(sourceMetadata);
            return document;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load document", e);
        }
    }

    static DocumentType detectDocumentType(String pathToFile) {
        if (pathToFile.endsWith(".txt")) {
            return TXT;
        }

        if (pathToFile.endsWith(".html")
                || pathToFile.endsWith(".htm")
                || pathToFile.endsWith(".xhtml")) {
            return HTML;
        }

        if (pathToFile.endsWith(".pdf")) {
            return PDF;
        }

        throw new UnsupportedDocumentTypeException(pathToFile);
    }

    static DocumentParser parserFor(DocumentType type) {
        switch (type) {
            case TXT:
            case HTML:
                return new TextDocumentParser(type);
            case PDF:
                return new PdfDocumentParser();
            default:
                throw new RuntimeException(String.format("Cannot find parser for document type '%s'", type));
        }
    }
}
