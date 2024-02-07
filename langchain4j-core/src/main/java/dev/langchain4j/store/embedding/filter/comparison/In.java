package dev.langchain4j.store.embedding.filter.comparison;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.MetadataFilter;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Collection;
import java.util.HashSet;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.store.embedding.filter.comparison.TypeChecker.ensureSameType;
import static java.util.Collections.unmodifiableSet;

@ToString
@EqualsAndHashCode
public class In implements MetadataFilter {

    private final String key;
    private final Collection<?> comparisonValues;

    public In(String key, Collection<?> comparisonValues) {
        this.key = ensureNotBlank(key, "key");
        this.comparisonValues = unmodifiableSet(new HashSet<>(ensureNotEmpty(comparisonValues, "comparisonValues")));
    }

    public String key() {
        return key;
    }

    public Collection<?> comparisonValues() {
        return comparisonValues;
    }

    @Override
    public boolean test(Metadata metadata) {
        Object actualValue = metadata.getObject(key);
        if (actualValue == null) {
            return false;
        }

        ensureSameType(actualValue, comparisonValues.iterator().next(), key);
        return comparisonValues.contains(actualValue);
    }
}
