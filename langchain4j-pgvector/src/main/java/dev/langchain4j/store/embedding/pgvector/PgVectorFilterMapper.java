package dev.langchain4j.store.embedding.pgvector;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.AbstractMap.SimpleEntry;

abstract class PgVectorFilterMapper {

    static final Map<Class<?>, String> SQL_TYPE_MAP = Stream.of(
                    new SimpleEntry<>(Integer.class, "int"),
                    new SimpleEntry<>(Long.class, "bigint"),
                    new SimpleEntry<>(Float.class, "float"),
                    new SimpleEntry<>(Double.class, "float8"),
                    new SimpleEntry<>(String.class, "text"),
                    new SimpleEntry<>(UUID.class, "uuid"),
                    new SimpleEntry<>(Boolean.class, "boolean"),
                    // Default
                    new SimpleEntry<>(Object.class, "text"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    public String map(Filter filter) {
        if (filter instanceof IsEqualTo to) {
            return mapEqual(to);
        } else if (filter instanceof IsNotEqualTo to) {
            return mapNotEqual(to);
        } else if (filter instanceof IsGreaterThan than) {
            return mapGreaterThan(than);
        } else if (filter instanceof IsGreaterThanOrEqualTo to) {
            return mapGreaterThanOrEqual(to);
        } else if (filter instanceof IsLessThan than) {
            return mapLessThan(than);
        } else if (filter instanceof IsLessThanOrEqualTo to) {
            return mapLessThanOrEqual(to);
        } else if (filter instanceof IsIn in) {
            return mapIn(in);
        } else if (filter instanceof IsNotIn in) {
            return mapNotIn(in);
        } else if (filter instanceof And and) {
            return mapAnd(and);
        } else if (filter instanceof Not not) {
            return mapNot(not);
        } else if (filter instanceof Or or) {
            return mapOr(or);
        } else {
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private String mapEqual(IsEqualTo isEqualTo) {
        String key = formatKey(isEqualTo.key(), isEqualTo.comparisonValue().getClass());
        return "%s is not null and %s = %s".formatted(key, key,
                formatValue(isEqualTo.comparisonValue()));
    }

    private String mapNotEqual(IsNotEqualTo isNotEqualTo) {
        String key = formatKey(isNotEqualTo.key(), isNotEqualTo.comparisonValue().getClass());
        return "%s is null or %s != %s".formatted(key, key,
                formatValue(isNotEqualTo.comparisonValue()));
    }

    private String mapGreaterThan(IsGreaterThan isGreaterThan) {
        return "%s > %s".formatted(formatKey(isGreaterThan.key(), isGreaterThan.comparisonValue().getClass()),
                formatValue(isGreaterThan.comparisonValue()));
    }

    private String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
        return "%s >= %s".formatted(formatKey(isGreaterThanOrEqualTo.key(), isGreaterThanOrEqualTo.comparisonValue().getClass()),
                formatValue(isGreaterThanOrEqualTo.comparisonValue()));
    }

    private String mapLessThan(IsLessThan isLessThan) {
        return "%s < %s".formatted(formatKey(isLessThan.key(), isLessThan.comparisonValue().getClass()),
                formatValue(isLessThan.comparisonValue()));
    }

    private String mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo) {
        return "%s <= %s".formatted(formatKey(isLessThanOrEqualTo.key(), isLessThanOrEqualTo.comparisonValue().getClass()),
                formatValue(isLessThanOrEqualTo.comparisonValue()));
    }

    private String mapIn(IsIn isIn) {
        return "%s in %s".formatted(formatKeyAsString(isIn.key()), formatValuesAsString(isIn.comparisonValues()));
    }

    private String mapNotIn(IsNotIn isNotIn) {
        String key = formatKeyAsString(isNotIn.key());
        return "%s is null or %s not in %s".formatted(key, key, formatValuesAsString(isNotIn.comparisonValues()));
    }

    private String mapAnd(And and) {
        return "%s and %s".formatted(map(and.left()), map(and.right()));
    }

    private String mapNot(Not not) {
        return "not(%s)".formatted(map(not.expression()));
    }

    private String mapOr(Or or) {
        return "(%s or %s)".formatted(map(or.left()), map(or.right()));
    }

    abstract String formatKey(String key, Class<?> valueType);

    abstract String formatKeyAsString(String key);

    String formatValue(Object value) {
        if (value instanceof String || value instanceof UUID) {
            return "'" + value + "'";
        } else {
            return value.toString();
        }
    }

    String formatValuesAsString(Collection<?> values) {
        return "(" + values.stream().map(v -> "'%s'".formatted(v))
                .collect(Collectors.joining(",")) + ")";
    }
}
