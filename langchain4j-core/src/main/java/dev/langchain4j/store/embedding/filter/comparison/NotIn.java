package dev.langchain4j.store.embedding.filter.comparison;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.MetadataFilter;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static dev.langchain4j.internal.ValidationUtils.*;
import static dev.langchain4j.store.embedding.filter.comparison.NumberComparator.containsAsBigDecimals;
import static dev.langchain4j.store.embedding.filter.comparison.TypeChecker.ensureTypesAreCompatible;
import static java.util.Collections.unmodifiableSet;

@ToString
@EqualsAndHashCode
public class NotIn implements MetadataFilter {

    private final String key;
    private final Collection<?> comparisonValues;

    public NotIn(String key, Collection<?> comparisonValues) {
        this.key = ensureNotBlank(key, "key");
        Set<?> copy = new HashSet<>(ensureNotEmpty(comparisonValues, "comparisonValues with key '" + key + "'"));
        this.comparisonValues = unmodifiableSet(copy);
        comparisonValues.forEach(value -> ensureNotNull(value, "comparisonValue with key '" + key + "'"));
    }

    public String key() {
        return key;
    }

    public Collection<?> comparisonValues() {
        return comparisonValues;
    }

    @Override
    public boolean test(Metadata metadata) {
        if (!metadata.containsKey(key)) {
            return true;
        }

        Object actualValue = metadata.getObject(key);
        ensureTypesAreCompatible(actualValue, comparisonValues.iterator().next(), key);

        if (comparisonValues.iterator().next() instanceof Number) {
            return !containsAsBigDecimals(actualValue, comparisonValues);
        }

        return !comparisonValues.contains(actualValue);
    }
}
