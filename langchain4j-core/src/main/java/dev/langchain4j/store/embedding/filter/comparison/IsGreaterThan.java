package dev.langchain4j.store.embedding.filter.comparison;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;

import java.util.Objects;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.filter.comparison.NumberComparator.isGreaterThan;
import static dev.langchain4j.store.embedding.filter.comparison.TypeChecker.ensureTypeIsSupported;
import static dev.langchain4j.store.embedding.filter.comparison.TypeChecker.ensureTypesAreCompatible;

public class IsGreaterThan implements Filter {

    private final String key;
    private final Comparable<?> comparisonValue;

    /**
     * Creates a filter that matches when the value of the {@code key} metadata entry is greater than the given value.
     *
     * @param key             the metadata key
     * @param comparisonValue the value to compare with, see {@link Filter} for the supported types
     * @throws IllegalArgumentException if {@code key} is blank, or if {@code comparisonValue} is {@code null},
     *                                  of an unsupported type or not a finite number
     */
    public IsGreaterThan(String key, Comparable<?> comparisonValue) {
        this.key = ensureNotBlank(key, "key");
        this.comparisonValue =
                ensureTypeIsSupported(ensureNotNull(comparisonValue, "comparisonValue with key '" + key + "'"), key);
    }

    public String key() {
        return key;
    }

    public Comparable<?> comparisonValue() {
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
        ensureTypesAreCompatible(actualValue, comparisonValue, key);

        if (actualValue instanceof Number) {
            return isGreaterThan(actualValue, comparisonValue);
        }

        return ((Comparable) actualValue).compareTo(comparisonValue) > 0;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof IsGreaterThan other)) return false;

        return Objects.equals(this.key, other.key)
                && Objects.equals(this.comparisonValue, other.comparisonValue);
    }

    public int hashCode() {
        return Objects.hash(key, comparisonValue);
    }

    public String toString() {
        return "IsGreaterThan(key=" + this.key + ", comparisonValue=" + this.comparisonValue + ")";
    }
}
