package dev.langchain4j.model.googleai.jsonl;

import java.io.IOException;

public interface JsonLinesWriter extends AutoCloseable {
    /**
     * Writes a single object as a JSON line.
     *
     * @param object the object to serialize and write
     * @throws IOException if an I/O error occurs
     */
    void write(Object object) throws IOException;

    /**
     * Writes multiple objects as JSON lines.
     *
     * @param objects the objects to serialize and write
     * @throws IOException if an I/O error occurs
     */
    void write(Iterable<?> objects) throws IOException;

    /**
     * Flushes any buffered data to the underlying output.
     *
     * @throws IOException if an I/O error occurs
     */
    void flush() throws IOException;

    @Override
    void close() throws IOException;
}
