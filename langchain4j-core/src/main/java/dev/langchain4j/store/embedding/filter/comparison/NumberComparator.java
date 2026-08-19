package dev.langchain4j.store.embedding.filter.comparison;

import dev.langchain4j.Internal;
import java.math.BigDecimal;
import java.util.Collection;

@Internal
class NumberComparator {

    static int compareAsBigDecimals(Object actualNumber, Object comparisonNumber) {
        // BigDecimal has no representation for NaN or +/-Infinity, and Double.toString
        // renders them as "NaN"/"Infinity", which its String constructor rejects. Metadata
        // accepts Float and Double without excluding those values, so fall back to
        // Double.compare rather than letting a NumberFormatException escape a predicate.
        if (isNonFinite(actualNumber) || isNonFinite(comparisonNumber)) {
            return Double.compare(((Number) actualNumber).doubleValue(), ((Number) comparisonNumber).doubleValue());
        }
        return new BigDecimal(actualNumber.toString()).compareTo(new BigDecimal(comparisonNumber.toString()));
    }

    static boolean containsAsBigDecimals(Object actualNumber, Collection<?> comparisonNumbers) {
        return comparisonNumbers.stream()
                .anyMatch(comparisonNumber -> compareAsBigDecimals(actualNumber, comparisonNumber) == 0);
    }

    /**
     * Returns true for NaN and +/-Infinity, the only {@link Number} values BigDecimal cannot hold.
     * Every other supported metadata number type (Integer, Long) is always finite.
     */
    private static boolean isNonFinite(Object number) {
        if (number instanceof Double doubleValue) {
            return !Double.isFinite(doubleValue);
        }
        if (number instanceof Float floatValue) {
            return !Float.isFinite(floatValue);
        }
        return false;
    }
}
