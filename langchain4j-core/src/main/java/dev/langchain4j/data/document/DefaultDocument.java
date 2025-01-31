package dev.langchain4j.data.document;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * A default implementation of a {@link Document}.
 */
public record DefaultDocument(String text, Metadata metadata) implements Document {

    public DefaultDocument(String text) {
        this(text, new Metadata());
    }

    public DefaultDocument(String text, Metadata metadata) {
        this.text = ensureNotBlank(text, "text");
        this.metadata = ensureNotNull(metadata, "metadata");
    }

    @Override
    public String metadata(String key) {
        return metadata.get(key);
    }

    @Override
    public String toString() {
        return "DefaultDocument {"
                + " text = " + quoted(text)  // todo: Be careful with PII
                + " metadata = " + metadata.toMap()
                + " }";
    }
}
