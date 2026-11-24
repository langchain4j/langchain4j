package dev.langchain4j.store.embedding.filter.comparison;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.store.embedding.filter.comparison.NumberComparator.isIn;
import static dev.langchain4j.store.embedding.filter.comparison.TypeChecker.ensureTypesAreSupported;
import static dev.langchain4j.store.embedding.filter.comparison.TypeChecker.ensureTypesAreCompatible;
import static dev.langchain4j.store.embedding.filter.comparison.UUIDComparator.containsAsUUID;
import static java.util.Collections.unmodifiableSet;

public class IsIn implements Filter {

    private final String key;
    private final Collection<?> comparisonValues;

    /**
     * Creates a filter that matches when the value of the {@code key} metadata entry is one of the given values.
     *
     * @param key              the metadata key
     * @param comparisonValues the values to compare with, not empty, see {@link Filter} for the supported types
     * @throws IllegalArgumentException if {@code key} is blank, if {@code comparisonValues} is {@code null} or
     *                                  empty, or if a value in it is {@code null}, of an unsupported type
     *                                  (for example a nested collection) or not a finite number
     */
    public IsIn(String key, Collection<?> comparisonValues) {
        this.key = ensureNotBlank(key, "key");
        Set<?> copy = new HashSet<>(ensureNotEmpty(comparisonValues, "comparisonValues with key '" + key + "'"));
        ensureTypesAreSupported(copy, key);
        this.comparisonValues = unmodifiableSet(copy);
    }

    public String key() {
        return key;
    }

    public Collection<?> comparisonValues() {
        return comparisonValues;
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
        ensureTypesAreCompatible(actualValue, comparisonValues.iterator().next(), key);

        if (comparisonValues.iterator().next() instanceof Number) {
            return isIn(actualValue, comparisonValues);
        }
        if (comparisonValues.iterator().next() instanceof UUID) {
            return containsAsUUID(actualValue, comparisonValues);
        }

        return comparisonValues.contains(actualValue);
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof IsIn other)) return false;

        return Objects.equals(this.key, other.key)
                && Objects.equals(this.comparisonValues, other.comparisonValues);
    }

    public int hashCode() {
        return Objects.hash(key, comparisonValues);
    }


    public String toString() {
        return "IsIn(key=" + this.key + ", comparisonValues=" + this.comparisonValues + ")";
    }
}
