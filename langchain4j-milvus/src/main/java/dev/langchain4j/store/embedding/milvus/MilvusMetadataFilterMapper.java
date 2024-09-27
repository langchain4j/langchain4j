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

    static String map(Filter filter, String metadataFiledName) {
        if (filter instanceof IsEqualTo) {
            return mapEqual((IsEqualTo) filter, metadataFiledName);
        } else if (filter instanceof IsNotEqualTo) {
            return mapNotEqual((IsNotEqualTo) filter, metadataFiledName);
        } else if (filter instanceof IsGreaterThan) {
            return mapGreaterThan((IsGreaterThan) filter, metadataFiledName);
        } else if (filter instanceof IsGreaterThanOrEqualTo) {
            return mapGreaterThanOrEqual((IsGreaterThanOrEqualTo) filter, metadataFiledName);
        } else if (filter instanceof IsLessThan) {
            return mapLessThan((IsLessThan) filter, metadataFiledName);
        } else if (filter instanceof IsLessThanOrEqualTo) {
            return mapLessThanOrEqual((IsLessThanOrEqualTo) filter, metadataFiledName);
        } else if (filter instanceof IsIn) {
            return mapIn((IsIn) filter, metadataFiledName);
        } else if (filter instanceof IsNotIn) {
            return mapNotIn((IsNotIn) filter, metadataFiledName);
        } else if (filter instanceof And) {
            return mapAnd((And) filter, metadataFiledName);
        } else if (filter instanceof Not) {
            return mapNot((Not) filter, metadataFiledName);
        } else if (filter instanceof Or) {
            return mapOr((Or) filter, metadataFiledName);
        } else {
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private static String mapEqual(IsEqualTo isEqualTo, String metadataFiledName) {
        return format("%s == %s", formatKey(isEqualTo.key(), metadataFiledName), formatValue(isEqualTo.comparisonValue()));
    }

    private static String mapNotEqual(IsNotEqualTo isNotEqualTo, String metadataFiledName) {
        return format("%s != %s", formatKey(isNotEqualTo.key(), metadataFiledName), formatValue(isNotEqualTo.comparisonValue()));
    }

    private static String mapGreaterThan(IsGreaterThan isGreaterThan, String metadataFiledName) {
        return format("%s > %s", formatKey(isGreaterThan.key(), metadataFiledName), formatValue(isGreaterThan.comparisonValue()));
    }

    private static String mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo, String metadataFiledName) {
        return format("%s >= %s", formatKey(isGreaterThanOrEqualTo.key(), metadataFiledName), formatValue(isGreaterThanOrEqualTo.comparisonValue()));
    }

    private static String mapLessThan(IsLessThan isLessThan, String metadataFiledName) {
        return format("%s < %s", formatKey(isLessThan.key(), metadataFiledName), formatValue(isLessThan.comparisonValue()));
    }

    private static String mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo, String metadataFiledName) {
        return format("%s <= %s", formatKey(isLessThanOrEqualTo.key(), metadataFiledName), formatValue(isLessThanOrEqualTo.comparisonValue()));
    }

    private static String mapIn(IsIn isIn, String metadataFiledName) {
        return format("%s in %s", formatKey(isIn.key(), metadataFiledName), formatValues(isIn.comparisonValues()));
    }

    private static String mapNotIn(IsNotIn isNotIn, String metadataFiledName) {
        return format("%s not in %s", formatKey(isNotIn.key(), metadataFiledName), formatValues(isNotIn.comparisonValues()));
    }

    private static String mapAnd(And and, String metadataFiledName) {
        return format("%s and %s", map(and.left(), metadataFiledName), map(and.right(), metadataFiledName));
    }

    private static String mapNot(Not not, String metadataFiledName) {
        return format("not(%s)", map(not.expression(), metadataFiledName));
    }

    private static String mapOr(Or or, String metadataFiledName) {
        return format("(%s or %s)", map(or.left(), metadataFiledName), map(or.right(), metadataFiledName));
    }

    private static String formatKey(String key, String metadataFiledName) {
        return metadataFiledName+"[\"" + key + "\"]";
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

