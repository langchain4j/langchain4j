package dev.langchain4j.data.document;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Objects;

/**
 * A default implementation of a {@link Document}.
 */
public record DefaultDocument(String text, Metadata metadata) implements Document {

    public DefaultDocument {
        ensureNotBlank(text, "text");
        ensureNotNull(metadata, "metadata");
    }

    public DefaultDocument(String text) {
        this(text, new Metadata());
    }

    @Override
    public String metadata(String key) {
        return metadata.get(key);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(text);
    }
}
