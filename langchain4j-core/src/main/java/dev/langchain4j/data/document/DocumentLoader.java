package dev.langchain4j.data.document;

import java.io.InputStream;

public class DocumentLoader {

    public static Document load(DocumentSource source, DocumentParser parser) {
        try (InputStream inputStream = source.inputStream()) {
            Document document = parser.parse(inputStream);
            source.metadata().asMap().forEach((key, value) -> document.metadata().add(key, value));
            return document;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load document", e);
        }
    }
}

// TODO document package check in all modules
// TODO store.embedding
