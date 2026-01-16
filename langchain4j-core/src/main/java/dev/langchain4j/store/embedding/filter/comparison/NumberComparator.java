package dev.langchain4j.store.embedding.filter.comparison;

import dev.langchain4j.Internal;

import java.math.BigDecimal;
import java.util.Collection;

@Internal
class NumberComparator {

    static int compareAsBigDecimals(Object actualNumber, Object comparisonNumber) {
        return new BigDecimal(actualNumber.toString()).compareTo(new BigDecimal(comparisonNumber.toString()));
    }

    static boolean containsAsBigDecimals(Object actualNumber, Collection<?> comparisonNumbers) {
        BigDecimal actualNumberAsBigDecimal = toBigDecimal(actualNumber);
        return comparisonNumbers.stream()
                .map(NumberComparator::toBigDecimal)
                .anyMatch(comparisonNumberAsBigDecimal ->
                        comparisonNumberAsBigDecimal.compareTo(actualNumberAsBigDecimal) == 0);
    }

    private static BigDecimal toBigDecimal(Object actualNumber) {
        if (actualNumber instanceof Integer integer) {
            return BigDecimal.valueOf(integer);
        } else if (actualNumber instanceof Long long1) {
            return BigDecimal.valueOf(long1);
        } else if (actualNumber instanceof Float float1) {
            return BigDecimal.valueOf(float1);
        } else if (actualNumber instanceof Double double1) {
            return BigDecimal.valueOf(double1);
        }

        throw new IllegalArgumentException("Unsupported type: " + actualNumber.getClass().getName());
    }
}
