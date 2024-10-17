package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static java.util.stream.Collectors.toList;

class MilvusMetadataFilterMapper {

    static String map(Filter filter, String metadataFieldName) {
        if (filter instanceof IsEqualTo to) {
            return mapEqual(to, metadataFieldName);
        } else if (filter instanceof IsNotEqualTo to) {
            return mapNotEqual(to, metadataFieldName);
        } else if (filter instanceof IsGreaterThan than) {
            return mapGreaterThan(than, metadataFieldName);
        } else if (filter instanceof IsGreaterThanOrEqualTo to) {
            return mapGreaterThanOrEqual(to, metadataFieldName);
        } else if (filter instanceof IsLessThan than) {
            return mapLessThan(than, metadataFieldName);
        } else if (filter instanceof IsLessThanOrEqualTo to) {
            return mapLessThanOrEqual(to, metadataFieldName);
        } else if (filter instanceof IsIn in) {
            return mapIn(in, metadataFieldName);
        } else if (filter instanceof IsNotIn in) {
            return mapNotIn(in, metadataFieldName);
        } else if (filter instanceof And and) {
            return mapAnd(and, metadataFieldName);
        } else if (filter instanceof Not not) {
            return mapNot(not, metadataFieldName);
        } else if (filter instanceof Or or) {
            return mapOr(or, metadataFieldName);
        } else {
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private static String mapEqual(IsEqualTo isEqualTo, String metadataFieldName) {
        return "%s == %s".formatted(formatKey(isEqualTo.key(), metadataFieldName), formatValue(isEqualTo.comparisonValue()));
    }

    private static String mapNotEqual(IsNotEqualTo isNotEqualTo, String metadataFieldName) {
        return "%s != %s".formatted(formatKey(isNotEqualTo.key(), metadataFieldName), formatValue(isNotEqualTo.comparisonValue()));
    }

    private static String mapGreaterThan(IsGreaterThan isGreaterThan, String metadataFieldName) {
        return "%s > %s".formatted(formatKey(isGreaterThan.key(), metadataFieldName), formatValue(isGreaterThan.comparisonValue()));
    }

    private static String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo, String metadataFieldName) {
        return "%s >= %s".formatted(formatKey(isGreaterThanOrEqualTo.key(), metadataFieldName), formatValue(isGreaterThanOrEqualTo.comparisonValue()));
    }

    private static String mapLessThan(IsLessThan isLessThan, String metadataFieldName) {
        return "%s < %s".formatted(formatKey(isLessThan.key(), metadataFieldName), formatValue(isLessThan.comparisonValue()));
    }

    private static String mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo, String metadataFieldName) {
        return "%s <= %s".formatted(formatKey(isLessThanOrEqualTo.key(), metadataFieldName), formatValue(isLessThanOrEqualTo.comparisonValue()));
    }

    private static String mapIn(IsIn isIn, String metadataFieldName) {
        return "%s in %s".formatted(formatKey(isIn.key(), metadataFieldName), formatValues(isIn.comparisonValues()));
    }

    private static String mapNotIn(IsNotIn isNotIn, String metadataFieldName) {
        return "%s not in %s".formatted(formatKey(isNotIn.key(), metadataFieldName), formatValues(isNotIn.comparisonValues()));
    }

    private static String mapAnd(And and, String metadataFieldName) {
        return "%s and %s".formatted(map(and.left(), metadataFieldName), map(and.right(), metadataFieldName));
    }

    private static String mapNot(Not not, String metadataFieldName) {
        return "not(%s)".formatted(map(not.expression(), metadataFieldName));
    }

    private static String mapOr(Or or, String metadataFieldName) {
        return "(%s or %s)".formatted(map(or.left(), metadataFieldName), map(or.right(), metadataFieldName));
    }

    private static String formatKey(String key, String metadataFieldName) {
        return metadataFieldName+"[\"" + key + "\"]";
    }

    private static String formatValue(Object value) {
        if (value instanceof String || value instanceof UUID) {
            return "\"" + value + "\"";
        } else {
            return value.toString();
        }
    }

    protected static List<String> formatValues(Collection<?> values) {
        return values.stream().map(MilvusMetadataFilterMapper::formatValue).collect(toList());
    }
}

