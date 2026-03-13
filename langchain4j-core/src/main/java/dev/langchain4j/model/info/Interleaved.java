package dev.langchain4j.model.info;

import java.util.Objects;

/**
 * Represents interleaved reasoning configuration for a model.
 */
public class Interleaved {
    private String field;

    public Interleaved() {}

    public Interleaved(String field) {
        this.field = field;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Interleaved that = (Interleaved) o;
        return Objects.equals(field, that.field);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field);
    }

    @Override
    public String toString() {
        return "Interleaved{" + "field='" + field + '\'' + '}';
    }
}
