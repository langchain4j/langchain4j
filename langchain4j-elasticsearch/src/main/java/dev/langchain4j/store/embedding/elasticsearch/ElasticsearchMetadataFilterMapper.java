package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static java.util.stream.Collectors.toList;

class ElasticsearchMetadataFilterMapper {

    static Query map(Filter filter) {
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

    private static Query mapEqual(IsEqualTo isEqualTo) {
        return new Query.Builder().bool(b -> b.filter(f -> f.term(t ->
                t.field(formatKey(isEqualTo.key(), isEqualTo.comparisonValue()))
                        .value(v -> v.anyValue(JsonData.of(isEqualTo.comparisonValue())))
        ))).build();
    }

    private static Query mapNotEqual(IsNotEqualTo isNotEqualTo) {
        return new Query.Builder().bool(b -> b.mustNot(mn -> mn.term(t ->
                t.field(formatKey(isNotEqualTo.key(), isNotEqualTo.comparisonValue()))
                        .value(v -> v.anyValue(JsonData.of(isNotEqualTo.comparisonValue())))
        ))).build();
    }

    private static Query mapGreaterThan(IsGreaterThan isGreaterThan) {
        return new Query.Builder().bool(b -> b.filter(f -> f.range(r ->
                r.field("metadata." + isGreaterThan.key())
                        .gt(JsonData.of(isGreaterThan.comparisonValue()))
        ))).build();
    }

    private static Query mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
        return new Query.Builder().bool(b -> b.filter(f -> f.range(r ->
                r.field("metadata." + isGreaterThanOrEqualTo.key())
                        .gte(JsonData.of(isGreaterThanOrEqualTo.comparisonValue()))
        ))).build();
    }

    private static Query mapLessThan(IsLessThan isLessThan) {
        return new Query.Builder().bool(b -> b.filter(f -> f.range(r ->
                r.field("metadata." + isLessThan.key())
                        .lt(JsonData.of(isLessThan.comparisonValue()))
        ))).build();
    }

    private static Query mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo) {
        return new Query.Builder().bool(b -> b.filter(f -> f.range(r ->
                r.field("metadata." + isLessThanOrEqualTo.key())
                        .lte(JsonData.of(isLessThanOrEqualTo.comparisonValue()))
        ))).build();
    }

    public static Query mapIn(IsIn isIn) {
        return new Query.Builder().bool(b -> b.filter(f -> f.terms(t ->
                t.field(formatKey(isIn.key(), isIn.comparisonValues()))
                        .terms(terms -> {
                            List<FieldValue> values = isIn.comparisonValues().stream()
                                    .map(it -> FieldValue.of(JsonData.of(it)))
                                    .collect(toList());
                            return terms.value(values);
                        })
        ))).build();
    }

    public static Query mapNotIn(IsNotIn isNotIn) {
        return new Query.Builder().bool(b -> b.mustNot(mn -> mn.terms(t ->
                t.field(formatKey(isNotIn.key(), isNotIn.comparisonValues()))
                        .terms(terms -> {
                            List<FieldValue> values = isNotIn.comparisonValues().stream()
                                    .map(it -> FieldValue.of(JsonData.of(it)))
                                    .collect(toList());
                            return terms.value(values);
                        })
        ))).build();
    }

    private static Query mapAnd(And and) {
        BoolQuery boolQuery = new BoolQuery.Builder()
                .must(map(and.left()))
                .must(map(and.right()))
                .build();
        return new Query.Builder().bool(boolQuery).build();
    }

    private static Query mapNot(Not not) {
        BoolQuery boolQuery = new BoolQuery.Builder()
                .mustNot(map(not.expression()))
                .build();
        return new Query.Builder().bool(boolQuery).build();
    }

    private static Query mapOr(Or or) {
        BoolQuery boolQuery = new BoolQuery.Builder()
                .should(map(or.left()))
                .should(map(or.right()))
                .build();
        return new Query.Builder().bool(boolQuery).build();
    }

    private static String formatKey(String key, Object comparisonValue) {
        if (comparisonValue instanceof String || comparisonValue instanceof UUID) {
            return "metadata." + key + ".keyword";
        } else {
            return "metadata." + key;
        }
    }

    private static String formatKey(String key, Collection<?> comparisonValues) {
        return formatKey(key, comparisonValues.iterator().next());
    }
}

