package dev.langchain4j.store.embedding.filter.comparison;

import dev.langchain4j.Internal;
import java.math.BigDecimal;
import java.util.Collection;

/**
 * Compares numeric metadata values.
 *
 * <p>Finite values are compared as {@link BigDecimal}s, so that values which differ but collapse onto the same
 * {@code double} (for example {@code 9007199254740992L} and {@code 9007199254740993L}) are not mistaken for
 * each other.
 *
 * <p>{@code BigDecimal} can represent neither infinity nor {@code NaN}, so non-finite values are compared as
 * {@code double}s instead: infinities keep their natural ordering, while {@code NaN} is not comparable to
 * anything at all, not even to itself. Every comparison involving {@code NaN} is therefore {@code false},
 * which is how {@code NaN} behaves in Java, in SQL and in most embedding stores.
 */
@Internal
class NumberComparator {

    static boolean isEqualTo(Object actualNumber, Object comparisonNumber) {
        return neitherIsNaN(actualNumber, comparisonNumber) && compare(actualNumber, comparisonNumber) == 0;
    }

    static boolean isGreaterThan(Object actualNumber, Object comparisonNumber) {
        return neitherIsNaN(actualNumber, comparisonNumber) && compare(actualNumber, comparisonNumber) > 0;
    }

    static boolean isGreaterThanOrEqualTo(Object actualNumber, Object comparisonNumber) {
        return neitherIsNaN(actualNumber, comparisonNumber) && compare(actualNumber, comparisonNumber) >= 0;
    }

    static boolean isLessThan(Object actualNumber, Object comparisonNumber) {
        return neitherIsNaN(actualNumber, comparisonNumber) && compare(actualNumber, comparisonNumber) < 0;
    }

    static boolean isLessThanOrEqualTo(Object actualNumber, Object comparisonNumber) {
        return neitherIsNaN(actualNumber, comparisonNumber) && compare(actualNumber, comparisonNumber) <= 0;
    }

    static boolean isIn(Object actualNumber, Collection<?> comparisonNumbers) {
        return comparisonNumbers.stream().anyMatch(comparisonNumber -> isEqualTo(actualNumber, comparisonNumber));
    }

    private static int compare(Object actualNumber, Object comparisonNumber) {
        if (isInfinite(actualNumber) || isInfinite(comparisonNumber)) {
            return Double.compare(doubleValue(actualNumber), doubleValue(comparisonNumber));
        }
        return new BigDecimal(actualNumber.toString()).compareTo(new BigDecimal(comparisonNumber.toString()));
    }

    private static boolean neitherIsNaN(Object actualNumber, Object comparisonNumber) {
        return !isNaN(actualNumber) && !isNaN(comparisonNumber);
    }

    private static boolean isNaN(Object number) {
        return number instanceof Double doubleValue && doubleValue.isNaN()
                || number instanceof Float floatValue && floatValue.isNaN();
    }

    private static boolean isInfinite(Object number) {
        return number instanceof Double doubleValue && doubleValue.isInfinite()
                || number instanceof Float floatValue && floatValue.isInfinite();
    }

    private static double doubleValue(Object number) {
        return ((Number) number).doubleValue();
    }
}
