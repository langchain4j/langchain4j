package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static java.lang.String.format;
import static java.util.stream.Collectors.toList;

class MilvusMetadataFilterMapper {

    static String map(Filter filter, String metadataFieldName) {
        if (filter instanceof IsEqualTo) {
            return mapEqual((IsEqualTo) filter, metadataFieldName);
        } else if (filter instanceof IsNotEqualTo) {
            return mapNotEqual((IsNotEqualTo) filter, metadataFieldName);
        } else if (filter instanceof IsGreaterThan) {
            return mapGreaterThan((IsGreaterThan) filter, metadataFieldName);
        } else if (filter instanceof IsGreaterThanOrEqualTo) {
            return mapGreaterThanOrEqual((IsGreaterThanOrEqualTo) filter, metadataFieldName);
        } else if (filter instanceof IsLessThan) {
            return mapLessThan((IsLessThan) filter, metadataFieldName);
        } else if (filter instanceof IsLessThanOrEqualTo) {
            return mapLessThanOrEqual((IsLessThanOrEqualTo) filter, metadataFieldName);
        } else if (filter instanceof IsIn) {
            return mapIn((IsIn) filter, metadataFieldName);
        } else if (filter instanceof IsNotIn) {
            return mapNotIn((IsNotIn) filter, metadataFieldName);
        } else if (filter instanceof And) {
            return mapAnd((And) filter, metadataFieldName);
        } else if (filter instanceof Not) {
            return mapNot((Not) filter, metadataFieldName);
        } else if (filter instanceof Or) {
            return mapOr((Or) filter, metadataFieldName);
        } else {
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private static String mapEqual(IsEqualTo isEqualTo, String metadataFieldName) {
        return format("%s == %s", formatKey(isEqualTo.key(), metadataFieldName), formatValue(isEqualTo.comparisonValue()));
    }

    private static String mapNotEqual(IsNotEqualTo isNotEqualTo, String metadataFieldName) {
        return format("%s != %s", formatKey(isNotEqualTo.key(), metadataFieldName), formatValue(isNotEqualTo.comparisonValue()));
    }

    private static String mapGreaterThan(IsGreaterThan isGreaterThan, String metadataFieldName) {
        return format("%s > %s", formatKey(isGreaterThan.key(), metadataFieldName), formatValue(isGreaterThan.comparisonValue()));
    }

    private static String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo, String metadataFieldName) {
        return format("%s >= %s", formatKey(isGreaterThanOrEqualTo.key(), metadataFieldName), formatValue(isGreaterThanOrEqualTo.comparisonValue()));
    }

    private static String mapLessThan(IsLessThan isLessThan, String metadataFieldName) {
        return format("%s < %s", formatKey(isLessThan.key(), metadataFieldName), formatValue(isLessThan.comparisonValue()));
    }

    private static String mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo, String metadataFieldName) {
        return format("%s <= %s", formatKey(isLessThanOrEqualTo.key(), metadataFieldName), formatValue(isLessThanOrEqualTo.comparisonValue()));
    }

    private static String mapIn(IsIn isIn, String metadataFieldName) {
        return format("%s in %s", formatKey(isIn.key(), metadataFieldName), formatValues(isIn.comparisonValues()));
    }

    private static String mapNotIn(IsNotIn isNotIn, String metadataFieldName) {
        return format("%s not in %s", formatKey(isNotIn.key(), metadataFieldName), formatValues(isNotIn.comparisonValues()));
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

