package dev.langchain4j.store.embedding.elasticsearch;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.json.JsonData;
import dev.langchain4j.store.embedding.filter.MetadataFilter;
import dev.langchain4j.store.embedding.filter.comparison.Equal;
import dev.langchain4j.store.embedding.filter.comparison.GreaterThan;
import dev.langchain4j.store.embedding.filter.comparison.In;
import dev.langchain4j.store.embedding.filter.comparison.LessThan;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.util.List;

import static java.util.stream.Collectors.toList;

class ElasticsearchMetadataFilterMapper {

    static Query map(MetadataFilter metadataFilter) {
        if (metadataFilter instanceof Equal) {
            return mapEqual((Equal) metadataFilter);
        } else if (metadataFilter instanceof GreaterThan) {
            return mapGreaterThan((GreaterThan) metadataFilter);
        } else if (metadataFilter instanceof LessThan) {
            return mapLessThan((LessThan) metadataFilter);
        } else if (metadataFilter instanceof In) {
            return mapIn((In) metadataFilter);
        } else if (metadataFilter instanceof And) {
            return mapAnd((And) metadataFilter);
        } else if (metadataFilter instanceof Not) {
            return mapNot((Not) metadataFilter);
        } else if (metadataFilter instanceof Or) {
            return mapOr((Or) metadataFilter);
        } else {
            throw new UnsupportedOperationException("Unsupported metadataFilter type: " + metadataFilter.getClass().getName());
        }
        // TODO map other expressions
    }

    private static Query mapEqual(Equal equal) {
        return new Query.Builder().bool(b -> b.filter(f -> f.term(t -> {
                    TermQuery.Builder field;
                    if (equal.comparisonValue() instanceof String) {
                        field = t.field("metadata." + equal.key() + ".keyword");
                    } else {
                        field = t.field("metadata." + equal.key());
                    }
                    return field.value(v -> v.anyValue(JsonData.of(equal.comparisonValue())));
                }
        ))).build();
    }

    private static Query mapGreaterThan(GreaterThan greaterThan) {
        return new Query.Builder().bool(b -> b.filter(f -> f.range(r ->
                r.field("metadata." + greaterThan.key()).gt(JsonData.of(greaterThan.comparisonValue()))
        ))).build();
    }

    private static Query mapLessThan(LessThan lessThan) {
        return new Query.Builder().bool(b -> b.filter(f -> f.range(r ->
                r.field("metadata." + lessThan.key()).lt(JsonData.of(lessThan.comparisonValue()))
        ))).build();
    }

    public static Query mapIn(In in) {
        return new Query.Builder().bool(b -> b.filter(f -> f.terms(t -> {
                    TermsQuery.Builder field;
                    if (in.comparisonValues().iterator().next() instanceof String) {
                        field = t.field("metadata." + in.key() + ".keyword");
                    } else {
                        field = t.field("metadata." + in.key());
                    }
                    return field.terms(terms -> {
                        List<FieldValue> values = in.comparisonValues().stream()
                                .map(it -> FieldValue.of(JsonData.of(it)))
                                .collect(toList());
                        return terms.value(values);
                    });
                }
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
}

