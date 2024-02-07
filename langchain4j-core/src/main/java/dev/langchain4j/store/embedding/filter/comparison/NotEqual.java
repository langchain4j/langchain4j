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
public class NotEqual implements MetadataFilter {

    // TODO remove in favor of Not(Equal) ?

    private final String key;
    private final Object comparisonValue;

    public NotEqual(String key, Object comparisonValue) {
        this.key = ensureNotBlank(key, "key");
        this.comparisonValue = ensureNotNull(comparisonValue, "comparisonValue");
    }

    public String key() {
        return key;
    }

    public Object comparisonValue() {
        return comparisonValue;
    }

    @Override
    public boolean test(Metadata metadata) {
        Object actualValue = metadata.getObject(key);
        if (actualValue == null) {
            return false;
        }

        ensureSameType(actualValue, comparisonValue, key);
        return !actualValue.equals(comparisonValue);
    }
}
