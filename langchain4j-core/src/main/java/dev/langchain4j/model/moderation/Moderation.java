package dev.langchain4j.model.moderation;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

public class Moderation {

    private final boolean flagged;
    private final String flaggedText;

    public Moderation() {
        this.flagged = false;
        this.flaggedText = null;
    }

    public Moderation(String flaggedText) {
        this.flagged = true;
        this.flaggedText = flaggedText;
    }

    public boolean flagged() {
        return flagged;
    }

    public String flaggedText() {
        return flaggedText;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Moderation that = (Moderation) o;
        return this.flagged == that.flagged
                && Objects.equals(this.flaggedText, that.flaggedText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flagged, flaggedText);
    }

    @Override
    public String toString() {
        return "Moderation {" +
                " flagged = " + flagged +
                ", flaggedText = " + quoted(flaggedText) +
                " }";
    }

    public static Moderation flagged(String flaggedText) {
        return new Moderation(flaggedText);
    }

    public static Moderation notFlagged() {
        return new Moderation();
    }
}
