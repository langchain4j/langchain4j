package dev.langchain4j.store.embedding.weaviate;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;
import io.weaviate.client.v1.filters.Operator;
import io.weaviate.client.v1.filters.WhereFilter;

public class WeaviateMetadataFilterMapper {

    static WhereFilter map(Filter filter) {
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

    private static WhereFilter mapEqual(IsEqualTo isEqualTo) {
        return WhereFilter.builder()
                .path(isEqualTo.key())
                .operator(Operator.Equal)
                .valueText(isEqualTo.comparisonValue().toString())
                .build();
    }

    private static WhereFilter mapNotEqual(IsNotEqualTo isNotEqualTo) {
        return WhereFilter.builder()
                .path(isNotEqualTo.key())
                .operator(Operator.NotEqual)
                .valueText(String.valueOf(isNotEqualTo.comparisonValue()))
                .build();
    }

    private static WhereFilter mapGreaterThan(IsGreaterThan isGreaterThan) {
        return WhereFilter.builder()
                .path(isGreaterThan.key())
                .operator(Operator.GreaterThan)
                .valueText(String.valueOf(isGreaterThan.comparisonValue()))
                .build();
    }

    private static WhereFilter mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
        return WhereFilter.builder()
                .path(isGreaterThanOrEqualTo.key())
                .operator(Operator.GreaterThanEqual)
                .valueText(String.valueOf(isGreaterThanOrEqualTo.comparisonValue()))
                .build();
    }

    private static WhereFilter mapLessThan(IsLessThan isLessThan) {
        return WhereFilter.builder()
                .path(isLessThan.key())
                .operator(Operator.LessThan)
                .valueText(String.valueOf(isLessThan.comparisonValue()))
                .build();
    }

    private static WhereFilter mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo) {
        return WhereFilter.builder()
                .path(isLessThanOrEqualTo.key())
                .operator(Operator.LessThanEqual)
                .valueText(String.valueOf(isLessThanOrEqualTo.comparisonValue()))
                .build();
    }

    private static WhereFilter mapIn(IsIn isIn) {
        return WhereFilter.builder()
                .path(isIn.key())
                .operator(Operator.ContainsAny)
                .valueText(isIn.comparisonValues().toArray(new String[0]))
                .build();
    }

    private static WhereFilter mapNotIn(IsNotIn isNotIn) {
        return WhereFilter.builder()
                .path(isNotIn.key())
                .operator(Operator.Not)
                .valueText(isNotIn.comparisonValues().toArray(new String[0]))
                .build();
    }

    private static WhereFilter mapAnd(And and) {
        return WhereFilter.builder()
                .operator(Operator.And)
                .operands(new WhereFilter[]{ map(and.left()), map(and.right()) })
                .build();
    }

    private static WhereFilter mapNot(Not not) {
        return WhereFilter.builder()
                .operator(Operator.Not)
                .operands(new WhereFilter[]{ map(not.expression()) })
                .build();
    }

    private static WhereFilter mapOr(Or or) {
        return WhereFilter.builder()
                .operator(Operator.Or)
                .operands(new WhereFilter[]{ map(or.left()), map(or.right()) })
                .build();
    }
}
