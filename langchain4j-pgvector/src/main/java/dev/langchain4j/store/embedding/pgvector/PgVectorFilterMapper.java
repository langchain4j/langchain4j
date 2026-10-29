package dev.langchain4j.store.embedding.pgvector;

import static java.lang.String.format;
import static java.util.AbstractMap.SimpleEntry;

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
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class PgVectorFilterMapper {

    static final Map<Class<?>, String> SQL_TYPE_MAP = Stream.of(
                    new SimpleEntry<>(Integer.class, "int"),
                    new SimpleEntry<>(Long.class, "bigint"),
                    new SimpleEntry<>(Float.class, "float"),
                    new SimpleEntry<>(Double.class, "float8"),
                    new SimpleEntry<>(Byte.class, "int"),
                    new SimpleEntry<>(Short.class, "int"),
                    new SimpleEntry<>(BigInteger.class, "numeric"),
                    new SimpleEntry<>(BigDecimal.class, "numeric"),
                    new SimpleEntry<>(String.class, "text"),
                    new SimpleEntry<>(UUID.class, "uuid"),
                    new SimpleEntry<>(Boolean.class, "boolean"),
                    // Default
                    new SimpleEntry<>(Object.class, "text"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    public WhereClause map(Filter filter) {
        List<String> parameters = new ArrayList<>();
        String sql = map(filter, parameters);
        return new WhereClause(sql, parameters);
    }

    private String map(Filter filter, List<String> parameters) {
        if (filter instanceof ContainsString containsString) {
            return mapContains(containsString, parameters);
        } else if (filter instanceof IsEqualTo isEqualTo) {
            return mapEqual(isEqualTo, parameters);
        } else if (filter instanceof IsNotEqualTo isNotEqualTo) {
            return mapNotEqual(isNotEqualTo, parameters);
        } else if (filter instanceof IsGreaterThan isGreaterThan) {
            return mapGreaterThan(isGreaterThan, parameters);
        } else if (filter instanceof IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
            return mapGreaterThanOrEqual(isGreaterThanOrEqualTo, parameters);
        } else if (filter instanceof IsLessThan isLessThan) {
            return mapLessThan(isLessThan, parameters);
        } else if (filter instanceof IsLessThanOrEqualTo isLessThanOrEqualTo) {
            return mapLessThanOrEqual(isLessThanOrEqualTo, parameters);
        } else if (filter instanceof IsIn isIn) {
            return mapIn(isIn, parameters);
        } else if (filter instanceof IsNotIn isNotIn) {
            return mapNotIn(isNotIn, parameters);
        } else if (filter instanceof And and) {
            return mapAnd(and, parameters);
        } else if (filter instanceof Not not) {
            return mapNot(not, parameters);
        } else if (filter instanceof Or or) {
            return mapOr(or, parameters);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private String mapContains(ContainsString containsString, List<String> parameters) {
        String key =
                formatKey(containsString.key(), containsString.comparisonValue().getClass());
        return format(
                "%s is not null and position(%s in %s) > 0",
                key, formatValue(containsString.comparisonValue(), parameters), key);
    }

    private String mapEqual(IsEqualTo isEqualTo, List<String> parameters) {
        String key = formatKey(isEqualTo.key(), isEqualTo.comparisonValue().getClass());
        return format("%s is not null and %s = %s", key, key, formatValue(isEqualTo.comparisonValue(), parameters));
    }

    private String mapNotEqual(IsNotEqualTo isNotEqualTo, List<String> parameters) {
        String key =
                formatKey(isNotEqualTo.key(), isNotEqualTo.comparisonValue().getClass());
        return format(
                "(%s is null or %s != %s)", key, key, formatValue(isNotEqualTo.comparisonValue(), parameters));
    }

    private String mapGreaterThan(IsGreaterThan isGreaterThan, List<String> parameters) {
        return format(
                "%s > %s",
                formatKey(isGreaterThan.key(), isGreaterThan.comparisonValue().getClass()),
                formatValue(isGreaterThan.comparisonValue(), parameters));
    }

    private String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo, List<String> parameters) {
        return format(
                "%s >= %s",
                formatKey(
                        isGreaterThanOrEqualTo.key(),
                        isGreaterThanOrEqualTo.comparisonValue().getClass()),
                formatValue(isGreaterThanOrEqualTo.comparisonValue(), parameters));
    }

    private String mapLessThan(IsLessThan isLessThan, List<String> parameters) {
        return format(
                "%s < %s",
                formatKey(isLessThan.key(), isLessThan.comparisonValue().getClass()),
                formatValue(isLessThan.comparisonValue(), parameters));
    }

    private String mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo, List<String> parameters) {
        return format(
                "%s <= %s",
                formatKey(
                        isLessThanOrEqualTo.key(),
                        isLessThanOrEqualTo.comparisonValue().getClass()),
                formatValue(isLessThanOrEqualTo.comparisonValue(), parameters));
    }

    private String mapIn(IsIn isIn, List<String> parameters) {
        return format(
                "%s in %s", formatKeyAsString(isIn.key()), formatValuesAsString(isIn.comparisonValues(), parameters));
    }

    private String mapNotIn(IsNotIn isNotIn, List<String> parameters) {
        String key = formatKeyAsString(isNotIn.key());
        return format(
                "(%s is null or %s not in %s)",
                key, key, formatValuesAsString(isNotIn.comparisonValues(), parameters));
    }

    private String mapAnd(And and, List<String> parameters) {
        return format("%s and %s", map(and.left(), parameters), map(and.right(), parameters));
    }

    private String mapNot(Not not, List<String> parameters) {
        return format("not(%s)", map(not.expression(), parameters));
    }

    private String mapOr(Or or, List<String> parameters) {
        return format("(%s or %s)", map(or.left(), parameters), map(or.right(), parameters));
    }

    abstract String formatKey(String key, Class<?> valueType);

    abstract String formatKeyAsString(String key);

    private static String formatValue(Object value, List<String> parameters) {
        parameters.add(String.valueOf(value));
        return "?";
    }

    private static String formatValuesAsString(Collection<?> values, List<String> parameters) {
        return "(" + values.stream().map(value -> formatValue(value, parameters)).collect(Collectors.joining(",")) + ")";
    }
}
