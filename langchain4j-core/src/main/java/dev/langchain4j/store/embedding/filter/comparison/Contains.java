package dev.langchain4j.store.embedding.filter.comparison;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.Objects;
import java.util.UUID;

/**
 * A filter that checks if the value of a metadata key contains a specific string.
 * The value of the metadata key must be a string or a UUID.
 */
public class Contains implements Filter {

    private final String key;
    private final String comparisonValue;

    public Contains(String key, String comparisonValue) {
        this.key = ensureNotBlank(key, "key");
        this.comparisonValue = ensureNotNull(comparisonValue, "comparisonValue with key '" + key + "'");
    }

    public String key() {
        return key;
    }

    public String comparisonValue() {
        return comparisonValue;
    }

    @Override
    public boolean test(Object object) {
        if (!(object instanceof Metadata metadata)) {
            return false;
        }

        if (!metadata.containsKey(key)) {
            return false;
        }

        Object actualValue = metadata.toMap().get(key);

        if (actualValue instanceof UUID uuid) {
            return uuid.toString().contains(comparisonValue);
        }

        if (actualValue instanceof String str) {
            return str.contains(comparisonValue);
        }

        throw illegalArgument(
                "Type mismatch: actual value of metadata key \"%s\" (%s) has type %s, "
                        + "while it is expected to be a string or a UUID",
                key, actualValue, actualValue.getClass().getName());
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof Contains other)) return false;

        return Objects.equals(this.key, other.key) && Objects.equals(this.comparisonValue, other.comparisonValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, comparisonValue);
    }

    @Override
    public String toString() {
        return "Contains(key=" + this.key + ", comparisonValue=" + this.comparisonValue + ")";
    }
}
