package dev.langchain4j.data.document;

import java.io.InputStream;

public class DocumentLoader {

    /**
     * Loads a document from the given source using the given parser.
     *
     * <p>Forwards the source Metadata to the parsed Document.
     *
     * @param source The source from which the document will be loaded.
     * @param parser The parser that will be used to parse the document.
     * @return The loaded document.
     */
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
