package dev.langchain4j.store.embedding.weaviate;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.*;
import io.weaviate.client.v1.filters.WhereFilter;
import io.weaviate.client.v1.filters.Operator;

import java.util.List;

public class WeaviateMetadataFilterMapper {

    private WeaviateMetadataFilterMapper() {
        // No instance possible
    }

    public static WhereFilter map(Filter filter) {
        if (filter == null) {
            return null;
        } else if (filter instanceof IsEqualTo) {
            return mapEqual((IsEqualTo) filter);
        } else if (filter instanceof IsNotEqualTo) {
            return mapNotEqual((IsNotEqualTo) filter);
        } else if (filter instanceof IsGreaterThan) {
            return mapIsGreaterThan((IsGreaterThan) filter);
        } else if (filter instanceof IsGreaterThanOrEqualTo) {
            return mapIsGreaterThanOrEqualTo((IsGreaterThanOrEqualTo) filter);
        } else if (filter instanceof IsLessThan) {
            return mapIsLessThan((IsLessThan) filter);
        } else if (filter instanceof IsLessThanOrEqualTo) {
            return mapIsLessThanOrEqualTo((IsLessThanOrEqualTo) filter);
        } else if (filter instanceof IsIn) {
            return mapIsIn((IsIn) filter);
        } else if (filter instanceof IsNotIn) {
            return mapIsNotIn((IsNotIn) filter);
        } else if (filter instanceof And) {
            return mapAnd((And) filter);
        } else if (filter instanceof Or) {
            return mapOr((Or) filter);
        } else if (filter instanceof Not) {
            return mapNot((Not) filter);
        } else {
            throw new UnsupportedOperationException("Unsupported filter type: " + filter.getClass().getName());
        }
    }

    private static WhereFilter mapEqual(IsEqualTo filter) {
        return WhereFilter.builder()
                .path(filter.key())
                .operator(Operator.Equal)
                .valueText(filter.comparisonValue().toString())
                .build();
    }

    private static WhereFilter mapNotEqual(IsNotEqualTo filter) {
        return WhereFilter.builder()
                .path(filter.key())
                .operator(Operator.NotEqual)
                .valueText(filter.comparisonValue().toString())
                .build();
    }

    private static WhereFilter mapIsGreaterThan(IsGreaterThan filter) {
        return WhereFilter.builder()
                .path(filter.key())
                .operator(Operator.GreaterThan)
                .valueText(filter.comparisonValue().toString())
                .build();
    }

    private static WhereFilter mapIsGreaterThanOrEqualTo(IsGreaterThanOrEqualTo filter) {
        return WhereFilter.builder()
                .path(filter.key())
                .operator(Operator.GreaterThanEqual)
                .valueText(filter.comparisonValue().toString())
                .build();
    }

    private static WhereFilter mapIsLessThan(IsLessThan filter) {
        return WhereFilter.builder()
                .path(filter.key())
                .operator(Operator.LessThan)
                .valueText(filter.comparisonValue().toString())
                .build();
    }

    private static WhereFilter mapIsLessThanOrEqualTo(IsLessThanOrEqualTo filter) {
        return WhereFilter.builder()
                .path(filter.key())
                .operator(Operator.LessThanEqual)
                .valueText(filter.comparisonValue().toString())
                .build();
    }

    private static WhereFilter mapIsIn(IsIn filter) {
        final List<String> comparisonValues = filter.comparisonValues().stream()
                .map(Object::toString)
                .toList();

        return WhereFilter.builder()
                .path(filter.key())
                .operator(Operator.ContainsAny)
                .valueText(String.join(",", comparisonValues))
                .build();
    }

    private static WhereFilter mapIsNotIn(IsNotIn filter) {
        final List<String> comparisonValues = filter.comparisonValues().stream()
                .map(Object::toString)
                .toList();

        return WhereFilter.builder()
                .path(filter.key())
                .operator(Operator.Not)
                .operands(WhereFilter.builder()
                                .path(filter.key())
                                .operator(Operator.ContainsAny)
                                .valueText(String.join(",", comparisonValues))
                                .build())
                .build();
    }

    private static WhereFilter mapAnd(And and) {
        WhereFilter leftFilter = map(and.left());
        WhereFilter rightFilter = map(and.right());

        return WhereFilter.builder()
                .operator(Operator.And)
                .operands(leftFilter, rightFilter)
                .build();
    }

    private static WhereFilter mapOr(Or orFilter) {
        WhereFilter leftFilter = map(orFilter.left());
        WhereFilter rightFilter = map(orFilter.right());

        return WhereFilter.builder()
                .operator(Operator.Or)
                .operands(leftFilter, rightFilter)
                .build();
    }

    private static WhereFilter mapNot(Not not) {
        Filter expression = not.expression();
        return WhereFilter.builder()
                .operator(Operator.Not)
                .operands(map(expression))
                .build();
    }
}
