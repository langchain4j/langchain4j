package dev.langchain4j.data.document;

import java.io.InputStream;

/**
 * Defines the interface for parsing an InputStream into a Document.
 * Different document types require specialized parsing logic.
 */
public interface DocumentParser {

    /**
     * Parses an InputStream into a Document.
     * The specific implementation of this method will depend on the type of the document being parsed.
     *
     * @param inputStream The InputStream that contains the content of the document.
     * @return The parsed Document.
     */
    Document parse(InputStream inputStream);
}
