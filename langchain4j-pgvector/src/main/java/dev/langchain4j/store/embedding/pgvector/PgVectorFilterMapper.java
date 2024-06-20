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

import static java.lang.String.format;
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
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private String mapEqual(IsEqualTo isEqualTo) {
        String key = formatKey(isEqualTo.key(), isEqualTo.comparisonValue().getClass());
        return format("%s is not null and %s = %s", key, key,
                formatValue(isEqualTo.comparisonValue()));
    }

    private String mapNotEqual(IsNotEqualTo isNotEqualTo) {
        String key = formatKey(isNotEqualTo.key(), isNotEqualTo.comparisonValue().getClass());
        return format("%s is null or %s != %s", key, key,
                formatValue(isNotEqualTo.comparisonValue()));
    }

    private String mapGreaterThan(IsGreaterThan isGreaterThan) {
        return format("%s > %s", formatKey(isGreaterThan.key(), isGreaterThan.comparisonValue().getClass()),
                formatValue(isGreaterThan.comparisonValue()));
    }

    private String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
        return format("%s >= %s", formatKey(isGreaterThanOrEqualTo.key(), isGreaterThanOrEqualTo.comparisonValue().getClass()),
                formatValue(isGreaterThanOrEqualTo.comparisonValue()));
    }

    private String mapLessThan(IsLessThan isLessThan) {
        return format("%s < %s", formatKey(isLessThan.key(), isLessThan.comparisonValue().getClass()),
                formatValue(isLessThan.comparisonValue()));
    }

    private String mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo) {
        return format("%s <= %s", formatKey(isLessThanOrEqualTo.key(), isLessThanOrEqualTo.comparisonValue().getClass()),
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
        if (value instanceof String || value instanceof UUID) {
            return "'" + value + "'";
        } else {
            return value.toString();
        }
    }

    String formatValuesAsString(Collection<?> values) {
        return "(" + values.stream().map(v -> String.format("'%s'", v))
                .collect(Collectors.joining(",")) + ")";
    }
}
