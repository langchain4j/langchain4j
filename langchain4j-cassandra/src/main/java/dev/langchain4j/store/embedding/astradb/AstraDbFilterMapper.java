package dev.langchain4j.store.embedding.astradb;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThan;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import io.stargate.sdk.utils.JsonUtils;
import java.util.ArrayList;
import java.util.Map;

class AstraDbFilterMapper {

    private AstraDbFilterMapper() {}

    static io.stargate.sdk.data.domain.query.Filter map(Filter filter) {
        return new io.stargate.sdk.data.domain.query.Filter(JsonUtils.marshallForDataApi(mapToObject(filter)));
    }

    private static Map<String, Object> mapToObject(Filter filter) {
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
        } else if (filter instanceof Or) {
            return mapOr((Or) filter);
        } else if (filter instanceof Not) {
            return mapNot((Not) filter);
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private static Map<String, Object> mapEqual(IsEqualTo filter) {
        return singletonMap(escapeFieldName(filter.key()), filter.comparisonValue());
    }

    private static Map<String, Object> mapNotEqual(IsNotEqualTo filter) {
        return singletonMap(escapeFieldName(filter.key()), singletonMap("$ne", filter.comparisonValue()));
    }

    private static Map<String, Object> mapGreaterThan(IsGreaterThan filter) {
        return singletonMap(escapeFieldName(filter.key()), singletonMap("$gt", filter.comparisonValue()));
    }

    private static Map<String, Object> mapGreaterThanOrEqual(IsGreaterThanOrEqualTo filter) {
        return singletonMap(escapeFieldName(filter.key()), singletonMap("$gte", filter.comparisonValue()));
    }

    private static Map<String, Object> mapLessThan(IsLessThan filter) {
        return singletonMap(escapeFieldName(filter.key()), singletonMap("$lt", filter.comparisonValue()));
    }

    private static Map<String, Object> mapLessThanOrEqual(IsLessThanOrEqualTo filter) {
        return singletonMap(escapeFieldName(filter.key()), singletonMap("$lte", filter.comparisonValue()));
    }

    private static Map<String, Object> mapIn(IsIn filter) {
        return singletonMap(
                escapeFieldName(filter.key()), singletonMap("$in", new ArrayList<>(filter.comparisonValues())));
    }

    private static Map<String, Object> mapNotIn(IsNotIn filter) {
        return singletonMap(
                escapeFieldName(filter.key()), singletonMap("$nin", new ArrayList<>(filter.comparisonValues())));
    }

    private static Map<String, Object> mapAnd(And filter) {
        return singletonMap("$and", asList(mapToObject(filter.left()), mapToObject(filter.right())));
    }

    private static Map<String, Object> mapOr(Or filter) {
        return singletonMap("$or", asList(mapToObject(filter.left()), mapToObject(filter.right())));
    }

    private static Map<String, Object> mapNot(Not filter) {
        return singletonMap("$not", mapToObject(filter.expression()));
    }

    private static String escapeFieldName(String fieldName) {
        return fieldName.replace("&", "&&").replace(".", "&.");
    }
}
