package dev.langchain4j.model.moderation;

import static dev.langchain4j.internal.Utils.quoted;

import java.io.Serializable;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Represents moderation status.
 */
public class Moderation implements Serializable {

    private final boolean flagged;

    @Nullable
    private final String flaggedText;

    /**
     * Construct a Moderation object that is not flagged.
     */
    public Moderation() {
        this.flagged = false;
        this.flaggedText = null;
    }

    /**
     * Construct a Moderation object that is flagged.
     *
     * @param flaggedText the text that was flagged.
     */
    public Moderation(@Nullable String flaggedText) {
        this.flagged = true;
        this.flaggedText = flaggedText;
    }

    /**
     * Returns true if the text was flagged.
     * @return true if the text was flagged, false otherwise.
     */
    public boolean flagged() {
        return flagged;
    }

    /**
     * Returns the text that was flagged.
     * @return the text that was flagged or <code>null</code> if the text was not flagged.
     */
    public @Nullable String flaggedText() {
        return flaggedText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Moderation that = (Moderation) o;
        return this.flagged == that.flagged && Objects.equals(this.flaggedText, that.flaggedText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flagged, flaggedText);
    }

    @Override
    public String toString() {
        return "Moderation {" + " flagged = " + flagged + ", flaggedText = " + quoted(flaggedText) + " }";
    }

    /**
     * Constructs a Moderation object that is flagged.
     * @param flaggedText the text that was flagged.
     * @return a Moderation object.
     */
    public static Moderation flagged(String flaggedText) {
        return new Moderation(flaggedText);
    }

    /**
     * Constructs a Moderation object that is not flagged.
     * @return a Moderation object.
     */
    public static Moderation notFlagged() {
        return new Moderation();
    }
}
