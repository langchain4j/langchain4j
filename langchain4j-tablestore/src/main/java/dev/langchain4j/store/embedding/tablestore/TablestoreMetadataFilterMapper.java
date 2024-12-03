package dev.langchain4j.store.embedding.tablestore;

import com.alicloud.openservices.tablestore.model.search.query.Query;
import com.alicloud.openservices.tablestore.model.search.query.QueryBuilders;
import com.alicloud.openservices.tablestore.model.search.query.TermsQuery;
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

import java.util.UUID;

class TablestoreMetadataFilterMapper {

    static Query map(Filter filter) {
        if (filter == null) {
            return QueryBuilders.matchAll().build();
        }
        if (filter instanceof IsEqualTo) {
            return mapEqual((IsEqualTo) filter);
        } else if (filter instanceof IsNotEqualTo) {
            return mapNotEqual((IsNotEqualTo) filter);
        } else if (filter instanceof IsTextMatch) {
            return mapMatch((IsTextMatch) filter);
        } else if (filter instanceof IsTextMatchPhrase) {
            return mapMatchPhrase((IsTextMatchPhrase) filter);
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

    private static Object transformType(Object object) {
        if (object instanceof Float) {
            object = ((Float) object).doubleValue();
        }
        if (object instanceof UUID) {
            object = ((UUID) object).toString();
        }
        return object;
    }

    private static Query mapEqual(IsEqualTo isEqualTo) {
        return QueryBuilders.term(isEqualTo.key(), transformType(isEqualTo.comparisonValue()))
                .build();
    }

    private static Query mapMatch(IsTextMatch isTextMatch) {
        return QueryBuilders.match(isTextMatch.key(), isTextMatch.comparisonValue())
                .build();
    }

    private static Query mapMatchPhrase(IsTextMatchPhrase isTextMatchPhrase) {
        return QueryBuilders.matchPhrase(isTextMatchPhrase.key(), isTextMatchPhrase.comparisonValue())
                .build();
    }

    private static Query mapNotEqual(IsNotEqualTo isNotEqualTo) {
        return QueryBuilders.bool()
                .mustNot(QueryBuilders.term(isNotEqualTo.key(), transformType(isNotEqualTo.comparisonValue())))
                .build();
    }

    private static Query mapGreaterThan(IsGreaterThan isGreaterThan) {
        return QueryBuilders.range(isGreaterThan.key())
                .greaterThan(transformType(isGreaterThan.comparisonValue()))
                .build();
    }

    private static Query mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
        return QueryBuilders.range(isGreaterThanOrEqualTo.key())
                .greaterThanOrEqual(transformType(isGreaterThanOrEqualTo.comparisonValue()))
                .build();
    }

    private static Query mapLessThan(IsLessThan isLessThan) {
        return QueryBuilders.range(isLessThan.key())
                .lessThan(transformType(isLessThan.comparisonValue()))
                .build();
    }

    private static Query mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo) {
        return QueryBuilders.range(isLessThanOrEqualTo.key())
                .lessThanOrEqual(transformType(isLessThanOrEqualTo.comparisonValue()))
                .build();
    }

    private static Query mapIn(IsIn isIn) {
        TermsQuery.Builder builder = QueryBuilders.terms(isIn.key());
        for (Object object : isIn.comparisonValues()) {
            builder.addTerm(transformType(object));
        }
        return builder.build();
    }

    private static Query mapNotIn(IsNotIn isNotIn) {
        TermsQuery.Builder builder = QueryBuilders.terms(isNotIn.key());
        for (Object object : isNotIn.comparisonValues()) {
            builder.addTerm(transformType(object));
        }
        return QueryBuilders.bool()
                .mustNot(builder.build())
                .build();
    }

    private static Query mapAnd(And and) {
        return QueryBuilders.bool()
                .must(map(and.left()))
                .must(map(and.right()))
                .build();
    }

    private static Query mapNot(Not not) {
        return QueryBuilders.bool()
                .mustNot(map(not.expression()))
                .build();
    }

    private static Query mapOr(Or or) {
        return QueryBuilders.bool()
                .should(map(or.left()))
                .should(map(or.right()))
                .build();
    }
}