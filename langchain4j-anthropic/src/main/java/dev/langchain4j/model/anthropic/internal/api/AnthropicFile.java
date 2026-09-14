package dev.langchain4j.model.anthropic.internal.api;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

/**
 * Metadata for a file stored via the Anthropic Files API ({@code /v1/files}).
 */
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(SnakeCaseStrategy.class)
public class AnthropicFile {

    /**
     * Unique identifier for the file (e.g. {@code "file_011CNvqeKgZx9CDdLYS8aUkV"}).
     */
    public String id;

    /**
     * The object type, always {@code "file"}.
     */
    public String type;

    /**
     * The name of the file.
     */
    public String filename;

    /**
     * The MIME type of the file.
     */
    public String mimeType;

    /**
     * The size of the file in bytes.
     */
    public Long sizeBytes;

    /**
     * RFC 3339 datetime string representing when the file was created.
     */
    public String createdAt;

    /**
     * Whether the file can be downloaded.
     */
    public Boolean downloadable;

    public AnthropicFile() {}

    @Override
    public int hashCode() {
        return Objects.hash(id, type, filename, mimeType, sizeBytes, createdAt, downloadable);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AnthropicFile)) return false;
        AnthropicFile that = (AnthropicFile) obj;
        return Objects.equals(id, that.id)
                && Objects.equals(type, that.type)
                && Objects.equals(filename, that.filename)
                && Objects.equals(mimeType, that.mimeType)
                && Objects.equals(sizeBytes, that.sizeBytes)
                && Objects.equals(createdAt, that.createdAt)
                && Objects.equals(downloadable, that.downloadable);
    }

    @Override
    public String toString() {
        return "AnthropicFile{" + "id='"
                + id + '\'' + ", type='"
                + type + '\'' + ", filename='"
                + filename + '\'' + ", mimeType='"
                + mimeType + '\'' + ", sizeBytes="
                + sizeBytes + ", createdAt='"
                + createdAt + '\'' + ", downloadable="
                + downloadable + '}';
    }
}
