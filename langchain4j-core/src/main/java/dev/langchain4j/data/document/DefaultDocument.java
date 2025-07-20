package dev.langchain4j.data.document;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * A default implementation of a {@link Document}.
 */
public class DefaultDocument implements Document {

    private final String text;
    private final Metadata metadata;

    public DefaultDocument(String text, Metadata metadata) {
        this.text = ensureNotBlank(text, "text");
        this.metadata = ensureNotNull(metadata, "metadata");
    }

    public DefaultDocument(String text) {
        this(text, new Metadata());
    }

    @Override
    public String text() {
        return text;
    }

    @Override
    public Metadata metadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (DefaultDocument) obj;
        return Objects.equals(this.text, that.text) &&
                Objects.equals(this.metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, metadata);
    }

    @Override
    public String toString() {
        return "DefaultDocument {" +
                " text = " + quoted(text) +
                ", metadata = " + metadata +
                " }";
    }
}
