package dev.langchain4j.store.embedding.milvus;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

class MilvusMetadataFilterMapper {

    static String map(Filter filter, String metadataFieldName) {
        if (filter instanceof ContainsString containsString) {
            return mapContains(containsString, metadataFieldName);
        } else if (filter instanceof IsEqualTo isEqualTo) {
            return mapEqual(isEqualTo, metadataFieldName);
        } else if (filter instanceof IsNotEqualTo isNotEqualTo) {
            return mapNotEqual(isNotEqualTo, metadataFieldName);
        } else if (filter instanceof IsGreaterThan isGreaterThan) {
            return mapGreaterThan(isGreaterThan, metadataFieldName);
        } else if (filter instanceof IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
            return mapGreaterThanOrEqual(isGreaterThanOrEqualTo, metadataFieldName);
        } else if (filter instanceof IsLessThan isLessThan) {
            return mapLessThan(isLessThan, metadataFieldName);
        } else if (filter instanceof IsLessThanOrEqualTo isLessThanOrEqualTo) {
            return mapLessThanOrEqual(isLessThanOrEqualTo, metadataFieldName);
        } else if (filter instanceof IsIn isIn) {
            return mapIn(isIn, metadataFieldName);
        } else if (filter instanceof IsNotIn isNotIn) {
            return mapNotIn(isNotIn, metadataFieldName);
        } else if (filter instanceof And and) {
            return mapAnd(and, metadataFieldName);
        } else if (filter instanceof Not not) {
            return mapNot(not, metadataFieldName);
        } else if (filter instanceof Or or) {
            return mapOr(or, metadataFieldName);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private static String mapContains(ContainsString containsString, String metadataFieldName) {
        // ContainsString is a literal substring match (see Filter / ContainsString#test, which uses
        // String#contains). Milvus LIKE treats % and _ as wildcards, so any % or _ in the user-supplied
        // value must be escaped to be matched literally; only the surrounding % we add are real wildcards.
        return format(
                "%s LIKE %s",
                formatKey(containsString.key(), metadataFieldName),
                formatLikePattern(containsString.comparisonValue()));
    }

    /**
     * Builds a quoted Milvus LIKE pattern that matches the given value as a literal substring. The value's
     * own LIKE wildcards ({@code %} and {@code _}) are escaped with a backslash so they are matched
     * literally, while the surrounding {@code %} characters remain wildcards for the "contains" semantics.
     * Backslash and double quote are escaped as in {@link #formatValue(Object)} so the value stays inside
     * the string literal.
     */
    private static String formatLikePattern(String value) {
        // Escape backslash first, then the LIKE wildcards % and _, then the string-literal double quote.
        String escaped = value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
                .replace("\"", "\\\"");
        return "\"%" + escaped + "%\"";
    }

    private static String mapEqual(IsEqualTo isEqualTo, String metadataFieldName) {
        return format(
                "%s == %s", formatKey(isEqualTo.key(), metadataFieldName), formatValue(isEqualTo.comparisonValue()));
    }

    private static String mapNotEqual(IsNotEqualTo isNotEqualTo, String metadataFieldName) {
        return format(
                "%s != %s",
                formatKey(isNotEqualTo.key(), metadataFieldName), formatValue(isNotEqualTo.comparisonValue()));
    }

    private static String mapGreaterThan(IsGreaterThan isGreaterThan, String metadataFieldName) {
        return format(
                "%s > %s",
                formatKey(isGreaterThan.key(), metadataFieldName), formatValue(isGreaterThan.comparisonValue()));
    }

    private static String mapGreaterThanOrEqual(
            IsGreaterThanOrEqualTo isGreaterThanOrEqualTo, String metadataFieldName) {
        return format(
                "%s >= %s",
                formatKey(isGreaterThanOrEqualTo.key(), metadataFieldName),
                formatValue(isGreaterThanOrEqualTo.comparisonValue()));
    }

    private static String mapLessThan(IsLessThan isLessThan, String metadataFieldName) {
        return format(
                "%s < %s", formatKey(isLessThan.key(), metadataFieldName), formatValue(isLessThan.comparisonValue()));
    }

    private static String mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo, String metadataFieldName) {
        return format(
                "%s <= %s",
                formatKey(isLessThanOrEqualTo.key(), metadataFieldName),
                formatValue(isLessThanOrEqualTo.comparisonValue()));
    }

    private static String mapIn(IsIn isIn, String metadataFieldName) {
        return format("%s in %s", formatKey(isIn.key(), metadataFieldName), formatValues(isIn.comparisonValues()));
    }

    private static String mapNotIn(IsNotIn isNotIn, String metadataFieldName) {
        return format(
                "%s not in %s", formatKey(isNotIn.key(), metadataFieldName), formatValues(isNotIn.comparisonValues()));
    }

    private static String mapAnd(And and, String metadataFieldName) {
        return format("%s and %s", map(and.left(), metadataFieldName), map(and.right(), metadataFieldName));
    }

    private static String mapNot(Not not, String metadataFieldName) {
        return format("not(%s)", map(not.expression(), metadataFieldName));
    }

    private static String mapOr(Or or, String metadataFieldName) {
        return format("(%s or %s)", map(or.left(), metadataFieldName), map(or.right(), metadataFieldName));
    }

    private static String formatKey(String key, String metadataFieldName) {
        return metadataFieldName + "[\"" + key + "\"]";
    }

    private static String formatValue(Object value) {
        if (value instanceof String stringValue) {
            // Escape backslashes first, then double quotes (Milvus treats backslash as the escape character)
            final String escapedValue = stringValue.replace("\\", "\\\\").replace("\"", "\\\"");
            return "\"" + escapedValue + "\"";
        } else if (value instanceof UUID) {
            return "\"" + value + "\"";
        } else {
            return value.toString();
        }
    }

    protected static List<String> formatValues(Collection<?> values) {
        return values.stream().map(MilvusMetadataFilterMapper::formatValue).collect(toList());
    }
}
