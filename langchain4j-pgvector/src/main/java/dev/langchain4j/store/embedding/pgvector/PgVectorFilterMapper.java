package dev.langchain4j.store.embedding.pgvector;

import static java.lang.String.format;
import static java.util.AbstractMap.SimpleEntry;

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
        if (filter instanceof ContainsString containsString) {
            return mapContains(containsString);
        } else if (filter instanceof IsEqualTo isEqualTo) {
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

    private String mapContains(ContainsString containsString) {
        String key =
                formatKey(containsString.key(), containsString.comparisonValue().getClass());
        return format("%s is not null and %s ~ %s", key, key, formatValue(containsString.comparisonValue()));
    }

    private String mapEqual(IsEqualTo isEqualTo) {
        String key = formatKey(isEqualTo.key(), isEqualTo.comparisonValue().getClass());
        return format("%s is not null and %s = %s", key, key, formatValue(isEqualTo.comparisonValue()));
    }

    private String mapNotEqual(IsNotEqualTo isNotEqualTo) {
        String key =
                formatKey(isNotEqualTo.key(), isNotEqualTo.comparisonValue().getClass());
        return format("%s is null or %s != %s", key, key, formatValue(isNotEqualTo.comparisonValue()));
    }

    private String mapGreaterThan(IsGreaterThan isGreaterThan) {
        return format(
                "%s > %s",
                formatKey(isGreaterThan.key(), isGreaterThan.comparisonValue().getClass()),
                formatValue(isGreaterThan.comparisonValue()));
    }

    private String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
        return format(
                "%s >= %s",
                formatKey(
                        isGreaterThanOrEqualTo.key(),
                        isGreaterThanOrEqualTo.comparisonValue().getClass()),
                formatValue(isGreaterThanOrEqualTo.comparisonValue()));
    }

    private String mapLessThan(IsLessThan isLessThan) {
        return format(
                "%s < %s",
                formatKey(isLessThan.key(), isLessThan.comparisonValue().getClass()),
                formatValue(isLessThan.comparisonValue()));
    }

    private String mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo) {
        return format(
                "%s <= %s",
                formatKey(
                        isLessThanOrEqualTo.key(),
                        isLessThanOrEqualTo.comparisonValue().getClass()),
                formatValue(isLessThanOrEqualTo.comparisonValue()));
    }

    private String mapIn(IsIn isIn) {
        return format("%s in %s", formatKeyAsString(isIn.key()), formatValuesAsString(isIn.comparisonValues()));
    }

    private String mapNotIn(IsNotIn isNotIn) {
        String key = formatKeyAsString(isNotIn.key());
        return format("%s is null or %s not in %s", key, key, formatValuesAsString(isNotIn.comparisonValues()));
    }

    private String mapAnd(And and) {
        return format("%s and %s", map(and.left()), map(and.right()));
    }

    private String mapNot(Not not) {
        return format("not(%s)", map(not.expression()));
    }

    private String mapOr(Or or) {
        return format("(%s or %s)", map(or.left()), map(or.right()));
    }

    abstract String formatKey(String key, Class<?> valueType);

    abstract String formatKeyAsString(String key);

    String formatValue(Object value) {
        if (value instanceof String stringValue) {
            final String escapedValue = stringValue.replace("'", "''");
            return "'" + escapedValue + "'";
        } else if (value instanceof UUID) {
            return "'" + value + "'";
        } else {
            return value.toString();
        }
    }

    String formatValuesAsString(Collection<?> values) {
        return "(" + values.stream().map(v -> format("'%s'", v)).collect(Collectors.joining(",")) + ")";
    }
}
