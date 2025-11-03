package dev.langchain4j.store.embedding.filter.comparison;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.Metadata;
import java.util.Objects;

/**
 * A filter that checks if the value of a metadata key contains a specific string.
 * The value of the metadata key must be a string.
 * <p>
 * ContainsString covers a subset (match whole string) of the like operator concept that why is considered a child of {@link Like}.
 */
public class ContainsString extends Like {

    private final String comparisonValue;

    public ContainsString(String key, String comparisonValue) {
        super(key, comparisonValue);
        this.comparisonValue = ensureNotNull(comparisonValue, "comparisonValue with key '" + key + "'");
    }

    @Override
    public String key() {
        return super.key();
    }

    public String comparisonValue() {
        return comparisonValue;
    }

    @Override
    public boolean test(Object object) {
        if (!(object instanceof Metadata metadata)) {
            return false;
        }

        if (!metadata.containsKey(key())) {
            return false;
        }

        Object actualValue = metadata.toMap().get(key());

        if (actualValue instanceof String str) {
            return str.contains(comparisonValue);
        }

        throw illegalArgument(
                "Type mismatch: actual value of metadata key \"%s\" (%s) has type %s, "
                        + "while it is expected to be a string",
                key(), actualValue, actualValue.getClass().getName());
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof ContainsString other)) return false;

        return Objects.equals(this.key(), other.key()) &&
                Objects.equals(this.comparisonValue, other.comparisonValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key(), comparisonValue);
    }

    @Override
    public String toString() {
        return "ContainsString(key=" + key() + ", comparisonValue=" + comparisonValue + ")";
    }
}
