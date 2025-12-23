package dev.langchain4j.http.client;

import dev.langchain4j.Experimental;

import java.util.Arrays;
import java.util.Objects;

/**
 * @since 1.10.0
 */
@Experimental
public class FormDataFile {

    private final String fileName;
    private final String contentType;
    private final byte[] content;

    public FormDataFile(String fileName, String contentType, byte[] content) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.content = content;
    }

    public String fileName() {
        return fileName;
    }

    public String contentType() {
        return contentType;
    }

    public byte[] content() {
        return content;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        FormDataFile that = (FormDataFile) o;
        return Objects.equals(fileName, that.fileName)
                && Objects.equals(contentType, that.contentType)
                && Objects.deepEquals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileName, contentType, Arrays.hashCode(content));
    }
}
