package dev.langchain4j.store.embedding.mariadb;

import static java.lang.String.format;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

abstract class MariaDbFilterMapper {

    private static final Set<Class<?>> TYPES_BOUND_AS_IS = Set.of(
            Integer.class,
            Long.class,
            Float.class,
            Double.class,
            Byte.class,
            Short.class,
            Boolean.class,
            BigInteger.class,
            BigDecimal.class);

    public WhereClause map(Filter filter) {
        List<Object> parameters = new ArrayList<>();
        String sql = map(filter, parameters);
        return new WhereClause(sql, parameters);
    }

    private String map(Filter filter, List<Object> parameters) {
        if (filter instanceof IsEqualTo) {
            return mapEqual((IsEqualTo) filter, parameters);
        } else if (filter instanceof IsNotEqualTo) {
            return mapNotEqual((IsNotEqualTo) filter, parameters);
        } else if (filter instanceof IsGreaterThan) {
            return mapGreaterThan((IsGreaterThan) filter, parameters);
        } else if (filter instanceof IsGreaterThanOrEqualTo) {
            return mapGreaterThanOrEqual((IsGreaterThanOrEqualTo) filter, parameters);
        } else if (filter instanceof IsLessThan) {
            return mapLessThan((IsLessThan) filter, parameters);
        } else if (filter instanceof IsLessThanOrEqualTo) {
            return mapLessThanOrEqual((IsLessThanOrEqualTo) filter, parameters);
        } else if (filter instanceof IsIn) {
            return mapIn((IsIn) filter, parameters);
        } else if (filter instanceof IsNotIn) {
            return mapNotIn((IsNotIn) filter, parameters);
        } else if (filter instanceof And) {
            return mapAnd((And) filter, parameters);
        } else if (filter instanceof Not) {
            return mapNot((Not) filter, parameters);
        } else if (filter instanceof Or) {
            return mapOr((Or) filter, parameters);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private String mapEqual(IsEqualTo isEqualTo, List<Object> parameters) {
        String key = formatKey(isEqualTo.key());
        return format("%s is not null and %s = %s", key, key, formatValue(isEqualTo.comparisonValue(), parameters));
    }

    private String mapNotEqual(IsNotEqualTo isNotEqualTo, List<Object> parameters) {
        String key = formatKey(isNotEqualTo.key());
        return format(
                "(%s is null or %s != %s)", key, key, formatValue(isNotEqualTo.comparisonValue(), parameters));
    }

    private String mapGreaterThan(IsGreaterThan isGreaterThan, List<Object> parameters) {
        return format(
                "%s > %s",
                formatKey(isGreaterThan.key()),
                formatValue(isGreaterThan.comparisonValue(), parameters));
    }

    private String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo, List<Object> parameters) {
        return format(
                "%s >= %s",
                formatKey(isGreaterThanOrEqualTo.key()),
                formatValue(isGreaterThanOrEqualTo.comparisonValue(), parameters));
    }

    private String mapLessThan(IsLessThan isLessThan, List<Object> parameters) {
        return format(
                "%s < %s", formatKey(isLessThan.key()), formatValue(isLessThan.comparisonValue(), parameters));
    }

    private String mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo, List<Object> parameters) {
        return format(
                "%s <= %s",
                formatKey(isLessThanOrEqualTo.key()),
                formatValue(isLessThanOrEqualTo.comparisonValue(), parameters));
    }

    private String mapIn(IsIn isIn, List<Object> parameters) {
        return format("%s in %s", formatKey(isIn.key()), formatValues(isIn.comparisonValues(), parameters));
    }

    private String mapNotIn(IsNotIn isNotIn, List<Object> parameters) {
        String key = formatKey(isNotIn.key());
        return format(
                "(%s is null or %s not in %s)", key, key, formatValues(isNotIn.comparisonValues(), parameters));
    }

    private String mapAnd(And and, List<Object> parameters) {
        return format("%s and %s", map(and.left(), parameters), map(and.right(), parameters));
    }

    private String mapNot(Not not, List<Object> parameters) {
        return format("not (%s)", map(not.expression(), parameters));
    }

    private String mapOr(Or or, List<Object> parameters) {
        return format("(%s or %s)", map(or.left(), parameters), map(or.right(), parameters));
    }

    abstract String formatKey(String key);

    private static String formatValue(Object value, List<Object> parameters) {
        parameters.add(TYPES_BOUND_AS_IS.contains(value.getClass()) ? value : String.valueOf(value));
        return "?";
    }

    private static String formatValues(Collection<?> values, List<Object> parameters) {
        return "(" + values.stream().map(value -> formatValue(value, parameters)).collect(Collectors.joining(",")) + ")";
    }
}
