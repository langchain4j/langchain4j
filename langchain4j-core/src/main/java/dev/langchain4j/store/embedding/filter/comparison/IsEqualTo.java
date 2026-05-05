package dev.langchain4j.store.embedding.filter.comparison;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.filter.comparison.ComparisonUtils.isEqualTo;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.Collection;
import java.util.Objects;

public class IsEqualTo implements Filter {

    private final String key;
    private final Object comparisonValue;

    public IsEqualTo(String key, Object comparisonValue) {
        this.key = ensureNotBlank(key, "key");
        this.comparisonValue = ensureNotNull(comparisonValue, "comparisonValue with key '" + key + "'");
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
            return false;
        }

        Object actualValue = metadata.toMap().get(key);
        if (actualValue instanceof Collection<?> actualValues) {
            return actualValues.stream().anyMatch(it -> isEqualTo(it, comparisonValue, key));
        }

        return isEqualTo(actualValue, comparisonValue, key);
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof IsEqualTo other)) return false;

        return Objects.equals(this.key, other.key) && Objects.equals(this.comparisonValue, other.comparisonValue);
    }

    public int hashCode() {
        return Objects.hash(key, comparisonValue);
    }

    public String toString() {
        return "IsEqualTo(key=" + this.key + ", comparisonValue=" + this.comparisonValue + ")";
    }
}
