package dev.langchain4j.data.document;

import java.io.InputStream;

/**
 * Defines the interface for parsing an {@link InputStream} into a {@link Document}.
 * Different document types require specialized parsing logic.
 */
public interface DocumentParser {

    /**
     * Parses a given {@link InputStream} into a {@link Document}.
     * The specific implementation of this method will depend on the type of the document being parsed.
     * <p>
     * Note: This method does not close the provided {@link InputStream} - it is the
     * caller's responsibility to manage the lifecycle of the stream.
     *
     * @param inputStream The {@link InputStream} that contains the content of the {@link Document}.
     * @return The parsed {@link Document}.
     * @throws BlankDocumentException when the parsed {@link Document} is blank/empty.
     */
    Document parse(InputStream inputStream);
}
