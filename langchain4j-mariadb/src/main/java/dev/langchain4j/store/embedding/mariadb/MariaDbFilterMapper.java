package dev.langchain4j.store.embedding.mariadb;

import static java.lang.String.format;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

abstract class MariaDbFilterMapper {

    public String map(Filter filter) {
        if (filter instanceof IsEqualTo) {
            return mapEqual((IsEqualTo) filter);
        } else if (filter instanceof IsNotEqualTo) {
            return mapNotEqual((IsNotEqualTo) filter);
        } else if (filter instanceof IsGreaterThan) {
            return mapGreaterThan((IsGreaterThan) filter);
        } else if (filter instanceof IsGreaterThanOrEqualTo) {
            return mapGreaterThanOrEqual((IsGreaterThanOrEqualTo) filter);
        } else if (filter instanceof IsLessThan) {
            return mapLessThan((IsLessThan) filter);
        } else if (filter instanceof IsLessThanOrEqualTo) {
            return mapLessThanOrEqual((IsLessThanOrEqualTo) filter);
        } else if (filter instanceof IsIn) {
            return mapIn((IsIn) filter);
        } else if (filter instanceof IsNotIn) {
            return mapNotIn((IsNotIn) filter);
        } else if (filter instanceof And) {
            return mapAnd((And) filter);
        } else if (filter instanceof Not) {
            return mapNot((Not) filter);
        } else if (filter instanceof Or) {
            return mapOr((Or) filter);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private String mapEqual(IsEqualTo isEqualTo) {
        String key = formatKey(isEqualTo.key());
        return format("%s is not null and %s = %s", key, key, formatValue(isEqualTo.comparisonValue()));
    }

    private String mapNotEqual(IsNotEqualTo isNotEqualTo) {
        String key = formatKey(isNotEqualTo.key());
        return format("(%s is null or %s != %s)", key, key, formatValue(isNotEqualTo.comparisonValue()));
    }

    private String mapGreaterThan(IsGreaterThan isGreaterThan) {
        return format("%s > %s", formatKey(isGreaterThan.key()), formatValue(isGreaterThan.comparisonValue()));
    }

    private String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
        return format(
                "%s >= %s",
                formatKey(isGreaterThanOrEqualTo.key()), formatValue(isGreaterThanOrEqualTo.comparisonValue()));
    }

    private String mapLessThan(IsLessThan isLessThan) {
        return format("%s < %s", formatKey(isLessThan.key()), formatValue(isLessThan.comparisonValue()));
    }

    private String mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo) {
        return format(
                "%s <= %s", formatKey(isLessThanOrEqualTo.key()), formatValue(isLessThanOrEqualTo.comparisonValue()));
    }

    private String mapIn(IsIn isIn) {
        return format("%s in %s", formatKey(isIn.key()), formatValue(isIn.comparisonValues()));
    }

    private String mapNotIn(IsNotIn isNotIn) {
        String key = formatKey(isNotIn.key());
        return format("(%s is null or %s not in %s)", key, key, formatValue(isNotIn.comparisonValues()));
    }

    private String mapAnd(And and) {
        return format("%s and %s", map(and.left()), map(and.right()));
    }

    private String mapNot(Not not) {
        return format("not (%s)", map(not.expression()));
    }

    private String mapOr(Or or) {
        return format("(%s or %s)", map(or.left()), map(or.right()));
    }

    abstract String formatKey(String key);

    String formatValue(Object value) {
        if (value instanceof final Collection<?> vals) {
            return "(" + vals.stream().map(this::formatValue).collect(Collectors.joining(",")) + ")";
        } else if (value instanceof String stringValue) {
            final String escapedValue = stringValue.replace("'", "''");
            return "'" + escapedValue + "'";
        } else if (value instanceof UUID) {
            return "'" + value + "'";
        } else {
            return value.toString();
        }
    }
}
