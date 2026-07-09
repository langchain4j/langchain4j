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
        BigDecimal actualNumberAsBigDecimal = new BigDecimal(actualNumber.toString());
        return comparisonNumbers.stream()
                .map(comparisonNumber -> new BigDecimal(comparisonNumber.toString()))
                .anyMatch(comparisonNumberAsBigDecimal ->
                        comparisonNumberAsBigDecimal.compareTo(actualNumberAsBigDecimal) == 0);
    }
}
