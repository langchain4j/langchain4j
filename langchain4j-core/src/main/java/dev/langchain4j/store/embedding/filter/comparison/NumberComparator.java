package dev.langchain4j.store.embedding.filter.comparison;

import java.math.BigDecimal;
import java.util.Collection;

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
        if (actualNumber instanceof Integer) {
            return BigDecimal.valueOf((int) actualNumber);
        } else if (actualNumber instanceof Long) {
            return BigDecimal.valueOf((long) actualNumber);
        } else if (actualNumber instanceof Float) {
            return BigDecimal.valueOf((float) actualNumber);
        } else if (actualNumber instanceof Double) {
            return BigDecimal.valueOf((double) actualNumber);
        }

        throw new IllegalArgumentException("Unsupported type: " + actualNumber.getClass().getName());
    }
}
