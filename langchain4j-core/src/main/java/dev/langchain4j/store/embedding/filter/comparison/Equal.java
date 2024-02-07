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
public class Equal implements MetadataFilter { // TODO make these classes package-private?

    private final String key;
    private final Object comparisonValue; // TODO validate type?

    public Equal(String key, Object comparisonValue) {
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
            // TODO distinguish between "no such key" and "value fot this key is null"? Deny setting a null value in Metadata?
            return false;
        }

        ensureSameType(actualValue, comparisonValue, key);
        return actualValue.equals(comparisonValue);
    }
}
