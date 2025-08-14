package dev.langchain4j.data.document;

import java.io.IOException;
import java.io.InputStream;

/**
 * Defines the interface for a Document source.
 * Documents can be loaded from various sources such as the file system, HTTP, FTP, etc.
 */
public interface DocumentSource {

    /**
     * Provides an {@link InputStream} to read the content of the document.
     * This method can be implemented to read from various sources like a local file or a network connection.
     *
     * @return An InputStream from which the document content can be read.
     * @throws IOException If an I/O error occurs while creating the InputStream.
     */
    InputStream inputStream() throws IOException;

    /**
     * Returns the metadata associated with the source of the document.
     * This could include details such as the source location, date of creation, owner, etc.
     *
     * @return A {@link Metadata} object containing information about the document
     *         source, such as {@link Document#FILE_NAME} and
     *         {@link Document#ABSOLUTE_DIRECTORY_PATH}.
     */
    Metadata metadata();
}
