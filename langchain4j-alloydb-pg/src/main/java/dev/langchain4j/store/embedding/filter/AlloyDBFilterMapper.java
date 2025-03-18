package dev.langchain4j.store.embedding.filter;

import dev.langchain4j.store.embedding.filter.comparison.ContainsString;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maps {@link Filter} objects into AlloyDB filter strings.
 */
public class AlloyDBFilterMapper {

    /**
     * Maps {@link Filter} into a string
     * @param filter the filter to be mapped
     * @return AlloyDB compatible filter string
     */
    public String map(Filter filter) {
        if (filter == null) {
            return "";
        }
        if (filter instanceof IsEqualTo isEqualTo) {
            return mapEqual(isEqualTo);
        } else if (filter instanceof IsNotEqualTo isNotEqualTo) {
            return mapNotEqual(isNotEqualTo);
        } else if (filter instanceof IsGreaterThan isGreaterThan) {
            return mapGreaterThan(isGreaterThan);
        } else if (filter instanceof IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
            return mapGreaterThanOrEqual(isGreaterThanOrEqualTo);
        } else if (filter instanceof IsLessThan isLessThan) {
            return mapLessThan(isLessThan);
        } else if (filter instanceof IsLessThanOrEqualTo isLessThanOrEqualTo) {
            return mapLessThanOrEqual(isLessThanOrEqualTo);
        } else if (filter instanceof IsIn isIn) {
            return mapIn(isIn);
        } else if (filter instanceof IsNotIn isNotIn) {
            return mapNotIn(isNotIn);
        } else if (filter instanceof ContainsString containsString) {
            return mapContainsString(containsString);
        } else if (filter instanceof And and) {
            return mapAnd(and);
        } else if (filter instanceof Not not) {
            return mapNot(not);
        } else if (filter instanceof Or or) {
            return mapOr(or);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private String mapEqual(IsEqualTo isEqualTo) {
        String key = isEqualTo.key();
        return String.format("\"%s\" IS NOT NULL AND \"%s\" = %s", key, key, formatValue(isEqualTo.comparisonValue()));
    }

    private String mapNotEqual(IsNotEqualTo isNotEqualTo) {
        String key = isNotEqualTo.key();
        return String.format("\"%s\" IS NULL OR \"%s\" != %s", key, key, formatValue(isNotEqualTo.comparisonValue()));
    }

    private String mapGreaterThan(IsGreaterThan isGreaterThan) {
        return String.format("\"%s\" > %s", isGreaterThan.key(), formatValue(isGreaterThan.comparisonValue()));
    }

    private String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
        return String.format(
                "\"%s\" >= %s", isGreaterThanOrEqualTo.key(), formatValue(isGreaterThanOrEqualTo.comparisonValue()));
    }

    private String mapLessThan(IsLessThan isLessThan) {
        return String.format("\"%s\" < %s", isLessThan.key(), formatValue(isLessThan.comparisonValue()));
    }

    private String mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo) {
        return String.format(
                "\"%s\" <= %s", isLessThanOrEqualTo.key(), formatValue(isLessThanOrEqualTo.comparisonValue()));
    }

    private String mapIn(IsIn isIn) {
        return String.format("\"%s\" IN %s", isIn.key(), formatValuesAsString(isIn.comparisonValues()));
    }

    private String mapNotIn(IsNotIn isNotIn) {
        String key = isNotIn.key();
        return String.format(
                "\"%s\" IS NULL OR \"%s\" NOT IN %s", key, key, formatValuesAsString(isNotIn.comparisonValues()));
    }

    private String mapContainsString(ContainsString containsString) {
        return String.format(
                "\"%s\" ILIKE '%%%s%%'", containsString.key(), formatValue(containsString.comparisonValue()));
    }

    private String mapAnd(And and) {
        return String.format("%s AND %s", map(and.left()), map(and.right()));
    }

    private String mapNot(Not not) {
        return String.format("NOT(%s)", map(not.expression()));
    }

    private String mapOr(Or or) {
        return String.format("(%s OR %s)", map(or.left()), map(or.right()));
    }

    String formatValue(Object value) {
        if (value instanceof String || value instanceof UUID) {
            return "'" + value + "'";
        } else {
            return value.toString();
        }
    }

    String formatValuesAsString(Collection<?> values) {
        return "(" + values.stream().map(v -> String.format("'%s'", v)).collect(Collectors.joining(",")) + ")";
    }
}
