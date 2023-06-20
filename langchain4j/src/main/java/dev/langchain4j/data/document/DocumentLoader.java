package dev.langchain4j.data.document;

import dev.langchain4j.data.document.parser.PdfDocumentParser;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.source.FileSystemSource;
import dev.langchain4j.data.document.source.UrlSource;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;

import static dev.langchain4j.data.document.DocumentType.TEXT;

public class DocumentLoader {

    private final DocumentSource source;
    private final DocumentParser parser;

    public DocumentLoader(DocumentSource source, DocumentParser parser) {
        this.source = source;
        this.parser = parser;
    }

    public Document load() {
        try (InputStream inputStream = source.inputStream()) {
            Document document = parser.parse(inputStream);
            Metadata sourceMetadata = source.sourceMetadata();
            document.metadata().mergeFrom(sourceMetadata);
            return document;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load document", e);
        }
    }

    public static DocumentLoader from(DocumentSource source, DocumentParser parser) {
        return new DocumentLoader(source, parser);
    }

    /**
     * Attempts to detect document type automatically
     */
    public static DocumentLoader from(String uri) {
        return from(sourceFor(uri), parserFor(detectDocumentType(uri)));
    }

    public static DocumentLoader from(String uri, DocumentType type) {
        return from(sourceFor(uri), parserFor(type));
    }

    private static DocumentSource sourceFor(String uriString) {
        URI uri = URI.create(uriString);
        String scheme = uri.getScheme();

        if (scheme.equals("file")) {
            return FileSystemSource.from(uri);
        } else if (scheme.equals("http") || scheme.equals("https")) {
            return UrlSource.from(uri);
        } else {
            throw new IllegalArgumentException(String.format("Unsupported URI scheme: '%s'", scheme));
        }
    }

    /**
     * Attempts to detect document type automatically
     */
    public static DocumentLoader from(Path path) {
        return from(FileSystemSource.from(path), parserFor(detectDocumentType(path.toString())));
    }

    public static DocumentLoader from(Path path, DocumentType type) {
        return from(FileSystemSource.from(path), parserFor(type));
    }

    /**
     * Attempts to detect document type automatically
     */
    public static DocumentLoader from(URL url) {
        return from(UrlSource.from(url), parserFor(detectDocumentType(url.toString())));
    }

    public static DocumentLoader from(URL url, DocumentType type) {
        return from(UrlSource.from(url), parserFor(type));
    }

    private static DocumentParser parserFor(DocumentType type) {
        switch (type) {
            case TEXT:
                return new TextDocumentParser();
            case PDF:
                return new PdfDocumentParser();
            default:
                throw new RuntimeException(String.format("Cannot find parser for document type '%s'", type));
        }
    }

    private static DocumentType detectDocumentType(String uri) {
        if (uri.endsWith("txt")) {
            return TEXT;
        }

        if (uri.endsWith("pdf")) {
            return DocumentType.PDF;
        }

        throw new RuntimeException("Cannot automatically detect document type for: " + uri);
    }
}
