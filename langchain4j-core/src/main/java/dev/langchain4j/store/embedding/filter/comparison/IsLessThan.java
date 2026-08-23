package dev.langchain4j.store.embedding.filter.comparison;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.filter.comparison.NumberComparator.isLessThan;
import static dev.langchain4j.store.embedding.filter.comparison.TypeChecker.ensureTypesAreCompatible;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.Objects;
import java.util.UUID;

public class IsLessThan implements Filter {

    private final String key;
    private final Comparable<?> comparisonValue;

    public IsLessThan(String key, Comparable<?> comparisonValue) {
        this.key = ensureNotBlank(key, "key");
        this.comparisonValue = ensureNotNull(comparisonValue, "comparisonValue with key '" + key + "'");
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
            return isLessThan(actualValue, comparisonValue);
        }

        if (comparisonValue instanceof UUID && actualValue instanceof String) {
            // Consistent with IsEqualTo and IsNotEqualTo: a UUID comparison value is
            // compared to the string representation of the value stored in metadata.
            return ((Comparable) actualValue).compareTo(comparisonValue.toString()) < 0;
        }

        return ((Comparable) actualValue).compareTo(comparisonValue) < 0;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof IsLessThan other)) return false;

        return Objects.equals(this.key, other.key) && Objects.equals(this.comparisonValue, other.comparisonValue);
    }

    public int hashCode() {
        return Objects.hash(key, comparisonValue);
    }

    public String toString() {
        return "IsLessThan(key=" + this.key + ", comparisonValue=" + this.comparisonValue + ")";
    }
}
