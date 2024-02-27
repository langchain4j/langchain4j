package dev.langchain4j.store.embedding.filter.comparison;

import java.math.BigDecimal;
import java.util.Collection;

class NumberComparator {

    static int compareAsBigDecimals(Object actualNumber, Object comparisonNumber) {
        return new BigDecimal(actualNumber.toString()).compareTo(new BigDecimal(comparisonNumber.toString()));
    }

    static boolean containsAsBigDecimals(Object actualNumber, Collection<?> comparisonNumbers) {
        BigDecimal actualNumberAsBigDecimal = new BigDecimal(actualNumber.toString());
        return comparisonNumbers.stream()
                .map(number -> new BigDecimal(number.toString()))
                .anyMatch(number -> number.equals(actualNumberAsBigDecimal));
    }
}
