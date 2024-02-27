package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import dev.langchain4j.store.embedding.filter.MetadataFilter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.util.Collection;
import java.util.List;

import static java.util.stream.Collectors.toList;

class ElasticsearchMetadataFilterMapper {

    static Query map(MetadataFilter metadataFilter) {
        if (metadataFilter instanceof Equal) {
            return mapEqual((Equal) metadataFilter);
        } else if (metadataFilter instanceof NotEqual) {
            return mapNotEqual((NotEqual) metadataFilter);
        } else if (metadataFilter instanceof GreaterThan) {
            return mapGreaterThan((GreaterThan) metadataFilter);
        } else if (metadataFilter instanceof GreaterThanOrEqual) {
            return mapGreaterThanOrEqual((GreaterThanOrEqual) metadataFilter);
        } else if (metadataFilter instanceof LessThan) {
            return mapLessThan((LessThan) metadataFilter);
        } else if (metadataFilter instanceof LessThanOrEqual) {
            return mapLessThanOrEqual((LessThanOrEqual) metadataFilter);
        } else if (metadataFilter instanceof In) {
            return mapIn((In) metadataFilter);
        } else if (metadataFilter instanceof NotIn) {
            return mapNotIn((NotIn) metadataFilter);
        } else if (metadataFilter instanceof And) {
            return mapAnd((And) metadataFilter);
        } else if (metadataFilter instanceof Not) {
            return mapNot((Not) metadataFilter);
        } else if (metadataFilter instanceof Or) {
            return mapOr((Or) metadataFilter);
        } else {
            throw new UnsupportedOperationException("Unsupported metadataFilter type: " + metadataFilter.getClass().getName());
        }
    }

    private static Query mapEqual(Equal equal) {
        return new Query.Builder().bool(b -> b.filter(f -> f.term(t ->
                t.field(formatKey(equal.key(), equal.comparisonValue()))
                        .value(v -> v.anyValue(JsonData.of(equal.comparisonValue())))
        ))).build();
    }

    private static Query mapNotEqual(NotEqual notEqual) {
        return new Query.Builder().bool(b -> b.mustNot(mn -> mn.term(t ->
                t.field(formatKey(notEqual.key(), notEqual.comparisonValue()))
                        .value(v -> v.anyValue(JsonData.of(notEqual.comparisonValue())))
        ))).build();
    }

    private static Query mapGreaterThan(GreaterThan greaterThan) {
        return new Query.Builder().bool(b -> b.filter(f -> f.range(r ->
                r.field("metadata." + greaterThan.key())
                        .gt(JsonData.of(greaterThan.comparisonValue()))
        ))).build();
    }

    private static Query mapGreaterThanOrEqual(GreaterThanOrEqual greaterThanOrEqual) {
        return new Query.Builder().bool(b -> b.filter(f -> f.range(r ->
                r.field("metadata." + greaterThanOrEqual.key())
                        .gte(JsonData.of(greaterThanOrEqual.comparisonValue()))
        ))).build();
    }

    private static Query mapLessThan(LessThan lessThan) {
        return new Query.Builder().bool(b -> b.filter(f -> f.range(r ->
                r.field("metadata." + lessThan.key())
                        .lt(JsonData.of(lessThan.comparisonValue()))
        ))).build();
    }

    private static Query mapLessThanOrEqual(LessThanOrEqual lessThanOrEqual) {
        return new Query.Builder().bool(b -> b.filter(f -> f.range(r ->
                r.field("metadata." + lessThanOrEqual.key())
                        .lte(JsonData.of(lessThanOrEqual.comparisonValue()))
        ))).build();
    }

    public static Query mapIn(In in) {
        return new Query.Builder().bool(b -> b.filter(f -> f.terms(t ->
                t.field(formatKey(in.key(), in.comparisonValues()))
                        .terms(terms -> {
                            List<FieldValue> values = in.comparisonValues().stream()
                                    .map(it -> FieldValue.of(JsonData.of(it)))
                                    .collect(toList());
                            return terms.value(values);
                        })
        ))).build();
    }

    public static Query mapNotIn(NotIn notIn) {
        return new Query.Builder().bool(b -> b.mustNot(mn -> mn.terms(t ->
                t.field(formatKey(notIn.key(), notIn.comparisonValues()))
                        .terms(terms -> {
                            List<FieldValue> values = notIn.comparisonValues().stream()
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
        if (comparisonValue instanceof String) {
            return "metadata." + key + ".keyword";
        } else {
            return "metadata." + key;
        }
    }

    private static String formatKey(String key, Collection<?> comparisonValues) {
        if (comparisonValues.iterator().next() instanceof String) {
            return "metadata." + key + ".keyword";
        } else {
            return "metadata." + key;
        }
    }
}

