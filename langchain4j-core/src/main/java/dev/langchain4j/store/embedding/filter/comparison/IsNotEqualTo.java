package dev.langchain4j.store.embedding.filter.comparison;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;

import java.util.Objects;
import java.util.UUID;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.filter.comparison.NumberComparator.isEqualTo;
import static dev.langchain4j.store.embedding.filter.comparison.TypeChecker.ensureTypeIsSupported;
import static dev.langchain4j.store.embedding.filter.comparison.TypeChecker.ensureTypesAreCompatible;

public class IsNotEqualTo implements Filter {

    private final String key;
    private final Object comparisonValue;

    /**
     * Creates a filter that matches when the value of the {@code key} metadata entry is not equal to the given value,
     * or when the key is missing.
     *
     * @param key             the metadata key
     * @param comparisonValue the value to compare with, see {@link Filter} for the supported types
     * @throws IllegalArgumentException if {@code key} is blank, or if {@code comparisonValue} is {@code null},
     *                                  of an unsupported type or not a finite number
     */
    public IsNotEqualTo(String key, Object comparisonValue) {
        this.key = ensureNotBlank(key, "key");
        this.comparisonValue =
                ensureTypeIsSupported(ensureNotNull(comparisonValue, "comparisonValue with key '" + key + "'"), key);
    }

    public String key() {
        return key;
    }

    public Object comparisonValue() {
        return comparisonValue;
    }

    @Override
    public boolean test(Object object) {
        if (!(object instanceof Metadata metadata)) {
            return false;
        }

        if (!metadata.containsKey(key)) {
            return true;
        }

        Object actualValue = metadata.toMap().get(key);
        ensureTypesAreCompatible(actualValue, comparisonValue, key);

        if (actualValue instanceof Number) {
            return !isEqualTo(actualValue, comparisonValue);
        }

        if (comparisonValue instanceof UUID && actualValue instanceof String) {
            return !actualValue.equals(comparisonValue.toString());
        }

        return !actualValue.equals(comparisonValue);
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof IsNotEqualTo other)) return false;

        return Objects.equals(this.key, other.key)
                && Objects.equals(this.comparisonValue, other.comparisonValue);
    }

    public int hashCode() {
        return Objects.hash(key, comparisonValue);
    }

    public String toString() {
        return "IsNotEqualTo(key=" + this.key + ", comparisonValue=" + this.comparisonValue + ")";
    }
}
