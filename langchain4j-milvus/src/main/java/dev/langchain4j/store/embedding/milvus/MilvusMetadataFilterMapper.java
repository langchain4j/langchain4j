package dev.langchain4j.store.embedding.milvus;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

class MilvusMetadataFilterMapper {

    static String map(Filter filter, String metadataFieldName, MilvusServerVersion serverVersion) {
        return map(filter, metadataFieldName, serverVersion, false);
    }

    private static String map(Filter filter, String metadataFieldName, MilvusServerVersion serverVersion, boolean insideNot) {
        if (filter instanceof ContainsString containsString) {
            return requireKeyIfInsideNot(
                    insideNot,
                    containsString.key(),
                    metadataFieldName,
                    mapContains(containsString, metadataFieldName, serverVersion));
        } else if (filter instanceof IsEqualTo isEqualTo) {
            return requireKeyIfInsideNot(
                    insideNot, isEqualTo.key(), metadataFieldName, mapEqual(isEqualTo, metadataFieldName));
        } else if (filter instanceof IsNotEqualTo isNotEqualTo) {
            return keyMissingOr(isNotEqualTo.key(), metadataFieldName, mapNotEqual(isNotEqualTo, metadataFieldName));
        } else if (filter instanceof IsGreaterThan isGreaterThan) {
            return requireKeyIfInsideNot(
                    insideNot, isGreaterThan.key(), metadataFieldName, mapGreaterThan(isGreaterThan, metadataFieldName));
        } else if (filter instanceof IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
            return requireKeyIfInsideNot(
                    insideNot,
                    isGreaterThanOrEqualTo.key(),
                    metadataFieldName,
                    mapGreaterThanOrEqual(isGreaterThanOrEqualTo, metadataFieldName));
        } else if (filter instanceof IsLessThan isLessThan) {
            return requireKeyIfInsideNot(
                    insideNot, isLessThan.key(), metadataFieldName, mapLessThan(isLessThan, metadataFieldName));
        } else if (filter instanceof IsLessThanOrEqualTo isLessThanOrEqualTo) {
            return requireKeyIfInsideNot(
                    insideNot,
                    isLessThanOrEqualTo.key(),
                    metadataFieldName,
                    mapLessThanOrEqual(isLessThanOrEqualTo, metadataFieldName));
        } else if (filter instanceof IsIn isIn) {
            return requireKeyIfInsideNot(insideNot, isIn.key(), metadataFieldName, mapIn(isIn, metadataFieldName));
        } else if (filter instanceof IsNotIn isNotIn) {
            return keyMissingOr(isNotIn.key(), metadataFieldName, mapNotIn(isNotIn, metadataFieldName));
        } else if (filter instanceof And and) {
            return format(
                    "%s and %s",
                    map(and.left(), metadataFieldName, serverVersion, insideNot),
                    map(and.right(), metadataFieldName, serverVersion, insideNot));
        } else if (filter instanceof Not not) {
            return format("not(%s)", map(not.expression(), metadataFieldName, serverVersion, true));
        } else if (filter instanceof Or or) {
            return format(
                    "(%s or %s)",
                    map(or.left(), metadataFieldName, serverVersion, insideNot),
                    map(or.right(), metadataFieldName, serverVersion, insideNot));
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private static String mapContains(
            ContainsString containsString, String metadataFieldName, MilvusServerVersion serverVersion) {
        // ContainsString is a literal substring match (see Filter / ContainsString#test, which uses
        // String#contains). Milvus LIKE treats % and _ as wildcards, so any % or _ in the user-supplied
        // value must be escaped to be matched literally; only the surrounding % we add are real wildcards.
        return format(
                "%s LIKE %s",
                formatKey(containsString.key(), metadataFieldName),
                formatLikePattern(containsString.comparisonValue(), serverVersion));
    }

    /**
     * Builds a quoted Milvus LIKE pattern that matches the given value as a literal substring. The value's
     * own LIKE wildcards ({@code %} and {@code _}) are escaped so they are matched literally, while the
     * surrounding {@code %} characters remain wildcards for the "contains" semantics.
     *
     * <p>Milvus resolves backslash escapes twice: once when reading the string literal out of the filter
     * expression, and again when interpreting the LIKE pattern. A wildcard therefore needs two backslashes
     * in the expression we send so that a single one reaches the pattern; a single backslash makes the
     * server reject the whole expression with a parse error.
     *
     * <p>In the LIKE pattern, Milvus 3 resolves a backslash before any character, while Milvus 2.5.10 to 2.6
     * resolve it only before {@code %} and {@code _} and keep it as a literal backslash otherwise. A backslash that
     * is part of the value therefore needs one more level of escaping on Milvus 3. Older Milvus 2 versions (2.4.24
     * and 2.5.4 were tested) behave like Milvus 3 here, so on them such a value does not match.
     *
     * <p>On Milvus 2, a value ending with {@code %} is known not to work, because Milvus itself mis-parses it.
     */
    private static String formatLikePattern(String value, MilvusServerVersion serverVersion) {
        // escape() covers the string literal layer. A wildcard has to survive the LIKE pattern layer as
        // well, so it gets two backslashes here and reaches the pattern with one.
        String literal = serverVersion.likePatternNeedsExtraBackslash() ? value.replace("\\", "\\\\") : value;
        String escaped = escape(literal).replace("%", "\\\\%").replace("_", "\\\\_");
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

    /**
     * Since Milvus 2.6.20, a comparison on a missing JSON key is UNKNOWN. Outside {@code not(...)} that already
     * drops the entry, as {@link Filter#test(Object)} does. Inside {@code not(...)} the comparison has to be false
     * instead, so that the negation matches entries without the key.
     * <p>
     * Before Milvus 2.6, {@code exists} has the lowest precedence, so it is wrapped in its own parentheses.
     */
    private static String requireKeyIfInsideNot(
            boolean insideNot, String key, String metadataFieldName, String comparison) {
        return insideNot
                ? format("((exists %s) and %s)", formatKey(key, metadataFieldName), comparison)
                : comparison;
    }

    /**
     * {@link IsNotEqualTo} and {@link IsNotIn} match entries without the key (see {@link Filter#test(Object)}),
     * which Milvus 2.6.20 and later only do when it is checked explicitly.
     */
    private static String keyMissingOr(String key, String metadataFieldName, String comparison) {
        return format("((not exists %s) or %s)", formatKey(key, metadataFieldName), comparison);
    }

    private static String formatKey(String key, String metadataFieldName) {
        return metadataFieldName + "[\"" + escape(key) + "\"]";
    }

    /**
     * Escapes a string that is embedded into a double-quoted Milvus string literal. This applies both to
     * metadata keys, which are embedded into the {@code metadata["..."]} accessor, and to string values.
     * Without it, a key or value containing a double quote could break out of the literal and inject
     * arbitrary Milvus filter expression syntax.
     */
    private static String escape(String value) {
        // Escape backslashes first, then double quotes (Milvus treats backslash as the escape character)
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String formatValue(Object value) {
        if (value instanceof String stringValue) {
            return "\"" + escape(stringValue) + "\"";
        } else if (value instanceof UUID) {
            return "\"" + value + "\"";
        } else if (value instanceof Boolean
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof Byte
                || value instanceof Short
                || value.getClass() == BigInteger.class
                || value.getClass() == BigDecimal.class) {
            return value.toString();
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported value type: " + value.getClass().getName());
        }
    }

    protected static List<String> formatValues(Collection<?> values) {
        return values.stream().map(MilvusMetadataFilterMapper::formatValue).collect(toList());
    }
}
