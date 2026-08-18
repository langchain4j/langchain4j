package dev.langchain4j.store.embedding.filter.comparison;

import static dev.langchain4j.store.embedding.filter.comparison.NumberComparator.compareAsBigDecimals;
import static dev.langchain4j.store.embedding.filter.comparison.NumberComparator.containsAsBigDecimals;
import static dev.langchain4j.store.embedding.filter.comparison.TypeChecker.ensureTypesAreCompatible;
import static dev.langchain4j.store.embedding.filter.comparison.UUIDComparator.containsAsUUID;

import dev.langchain4j.Internal;
import java.util.Collection;
import java.util.UUID;

@Internal
class ComparisonUtils {

    static boolean isEqualTo(Object actualValue, Object comparisonValue, String key) {
        ensureTypesAreCompatible(actualValue, comparisonValue, key);

        if (actualValue instanceof Number) {
            return compareAsBigDecimals(actualValue, comparisonValue) == 0;
        }

        if (comparisonValue instanceof UUID && actualValue instanceof String) {
            return actualValue.equals(comparisonValue.toString());
        }

        return actualValue.equals(comparisonValue);
    }

    static boolean isIn(Object actualValue, Collection<?> comparisonValues, String key) {
        Object comparisonValue = comparisonValues.iterator().next();
        ensureTypesAreCompatible(actualValue, comparisonValue, key);

        if (comparisonValue instanceof Number) {
            return containsAsBigDecimals(actualValue, comparisonValues);
        }
        if (comparisonValue instanceof UUID) {
            return containsAsUUID(actualValue, comparisonValues);
        }

        return comparisonValues.contains(actualValue);
    }

    private ComparisonUtils() {}
}
