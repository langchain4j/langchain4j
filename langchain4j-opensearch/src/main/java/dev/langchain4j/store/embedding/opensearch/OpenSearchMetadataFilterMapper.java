package dev.langchain4j.store.embedding.opensearch;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;

class OpenSearchMetadataFilterMapper {

    private static final String METADATA_PREFIX = "metadata.";
    private static final String KEYWORD_SUFFIX = ".keyword";

    private OpenSearchMetadataFilterMapper() {
        // Utility class
    }

    static Query map(Filter filter) {
        if (filter instanceof IsEqualTo isEqualTo) {
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

    private static Query mapEqual(IsEqualTo isEqualTo) {
        return new Query.Builder()
                .bool(b -> b.filter(f -> f.term(t -> t.field(formatKey(isEqualTo.key(), isEqualTo.comparisonValue()))
                        .value(toFieldValue(isEqualTo.comparisonValue())))))
                .build();
    }

    private static Query mapNotEqual(IsNotEqualTo isNotEqualTo) {
        return new Query.Builder()
                .bool(b -> b.mustNot(
                        mn -> mn.term(t -> t.field(formatKey(isNotEqualTo.key(), isNotEqualTo.comparisonValue()))
                                .value(toFieldValue(isNotEqualTo.comparisonValue())))))
                .build();
    }

    private static Query mapGreaterThan(IsGreaterThan isGreaterThan) {
        return new Query.Builder()
                .bool(b -> b.filter(f -> f.range(r -> r.field(METADATA_PREFIX + isGreaterThan.key())
                        .gt(JsonData.of(isGreaterThan.comparisonValue())))))
                .build();
    }

    private static Query mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
        return new Query.Builder()
                .bool(b -> b.filter(f -> f.range(r -> r.field(METADATA_PREFIX + isGreaterThanOrEqualTo.key())
                        .gte(JsonData.of(isGreaterThanOrEqualTo.comparisonValue())))))
                .build();
    }

    private static Query mapLessThan(IsLessThan isLessThan) {
        return new Query.Builder()
                .bool(b -> b.filter(f -> f.range(r ->
                        r.field(METADATA_PREFIX + isLessThan.key()).lt(JsonData.of(isLessThan.comparisonValue())))))
                .build();
    }

    private static Query mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo) {
        return new Query.Builder()
                .bool(b -> b.filter(f -> f.range(r -> r.field(METADATA_PREFIX + isLessThanOrEqualTo.key())
                        .lte(JsonData.of(isLessThanOrEqualTo.comparisonValue())))))
                .build();
    }

    public static Query mapIn(IsIn isIn) {
        return new Query.Builder()
                .bool(b -> b.filter(f -> f.terms(t -> t.field(formatKey(isIn.key(), isIn.comparisonValues()))
                        .terms(terms -> {
                            List<FieldValue> values = isIn.comparisonValues().stream()
                                    .map(OpenSearchMetadataFilterMapper::toFieldValue)
                                    .toList();
                            return terms.value(values);
                        }))))
                .build();
    }

    public static Query mapNotIn(IsNotIn isNotIn) {
        return new Query.Builder()
                .bool(b -> b.mustNot(mn -> mn.terms(t -> t.field(formatKey(isNotIn.key(), isNotIn.comparisonValues()))
                        .terms(terms -> {
                            List<FieldValue> values = isNotIn.comparisonValues().stream()
                                    .map(OpenSearchMetadataFilterMapper::toFieldValue)
                                    .toList();
                            return terms.value(values);
                        }))))
                .build();
    }

    private static Query mapAnd(And and) {
        BoolQuery boolQuery = new BoolQuery.Builder()
                .must(map(and.left()))
                .must(map(and.right()))
                .build();
        return new Query.Builder().bool(boolQuery).build();
    }

    private static Query mapNot(Not not) {
        BoolQuery boolQuery =
                new BoolQuery.Builder().mustNot(map(not.expression())).build();
        return new Query.Builder().bool(boolQuery).build();
    }

    private static Query mapOr(Or or) {
        BoolQuery boolQuery = new BoolQuery.Builder()
                .should(map(or.left()))
                .should(map(or.right()))
                .build();
        return new Query.Builder().bool(boolQuery).build();
    }

    private static FieldValue toFieldValue(Object value) {
        if (value instanceof String string) {
            return FieldValue.of(string);
        } else if (value instanceof Long longValue) {
            return FieldValue.of(longValue);
        } else if (value instanceof Integer integer) {
            return FieldValue.of(integer);
        } else if (value instanceof Double doubleValue) {
            return FieldValue.of(doubleValue);
        } else if (value instanceof Float floatValue) {
            return FieldValue.of(floatValue.doubleValue());
        } else if (value instanceof Boolean bool) {
            return FieldValue.of(bool);
        } else {
            return FieldValue.of(value.toString());
        }
    }

    private static String formatKey(String key, Object comparisonValue) {
        if (comparisonValue instanceof String || comparisonValue instanceof UUID) {
            return METADATA_PREFIX + key + KEYWORD_SUFFIX;
        } else {
            return METADATA_PREFIX + key;
        }
    }

    private static String formatKey(String key, Collection<?> comparisonValues) {
        return formatKey(key, comparisonValues.iterator().next());
    }
}
