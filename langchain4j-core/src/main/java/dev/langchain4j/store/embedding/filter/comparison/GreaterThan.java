package dev.langchain4j.store.embedding.filter.comparison;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.MetadataFilter;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.filter.comparison.TypeChecker.ensureSameType;

@ToString
@EqualsAndHashCode
public class GreaterThan implements MetadataFilter {

    private final String key;
    private final Comparable comparisonValue;

    public GreaterThan(String key, Comparable comparisonValue) {
        this.key = ensureNotBlank(key, "key");
        this.comparisonValue = ensureNotNull(comparisonValue, "comparisonValue");
    }

    public String key() {
        return key;
    }

    public Comparable comparisonValue() {
        return comparisonValue;
    }

    @Override
    public boolean test(Metadata metadata) {
        Object actualValue = metadata.getObject(key);
        if (actualValue == null) {
            return false;
        }

        if (comparisonValue instanceof Long && actualValue instanceof Integer) { // TODO more types, everywhere
            actualValue = Long.valueOf((Integer) actualValue); // TODO improve casting, everywhere
        }

        ensureSameType(actualValue, comparisonValue, key);
        return ((Comparable) actualValue).compareTo(comparisonValue) > 0;
    }
}
