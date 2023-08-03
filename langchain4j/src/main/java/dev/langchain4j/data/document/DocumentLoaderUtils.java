package dev.langchain4j.data.document;

import dev.langchain4j.data.document.parser.MSOfficeDocumentParser;
import dev.langchain4j.data.document.parser.PdfDocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;

import static dev.langchain4j.data.document.DocumentType.*;

class DocumentLoaderUtils {

    static Document load(DocumentSource source, DocumentParser parser) {
        try (InputStream inputStream = source.inputStream()) {
            Document document = parser.parse(inputStream);
            Metadata sourceMetadata = source.metadata();
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

        if (pathToFile.endsWith(".ppt")
                || pathToFile.endsWith(".pptx")
                || pathToFile.endsWith(".doc")
                || pathToFile.endsWith(".docx")
                || pathToFile.endsWith(".xls")
                || pathToFile.endsWith(".xlsx")) {
            return Arrays.stream(pathToFile.toUpperCase().split("\\."))
                    .sorted(Comparator.reverseOrder())
                    .findFirst()
                    .map(DocumentType::valueOf)
                    .get();
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
            case XLS:
            case XLSX:
            case DOC:
            case DOCX:
            case PPT:
            case PPTX:
                return new MSOfficeDocumentParser(type);
            default:
                throw new RuntimeException(String.format("Cannot find parser for document type '%s'", type));
        }
    }
}
