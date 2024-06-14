package dev.langchain4j.rag.content.retriever.azure.search;

import dev.langchain4j.store.embedding.filter.*;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.util.Collection;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class AzureAiSearchFilterMapper {

    public static String map(Filter filter) {
        if (filter == null) return "";

        if (isLogicalOperator(filter)) {
            return mapLogicalOperator(filter);
        } else {
            return mapComparisonFilter(filter);
        }
    }

    private static String mapLogicalOperator(Filter operator) {
        if (operator instanceof And) return  format(getLogicalFormat(operator), map(((And) operator).left()), map(((And) operator).right()));
        if (operator instanceof Or) return format(getLogicalFormat(operator), map(((Or) operator).left()), map(((Or) operator).right()));
        if (operator instanceof Not) return format(getLogicalFormat(operator), map(((Not) operator).expression()));
        throw new IllegalArgumentException("Unsupported operator: " + operator);
    }

    private static boolean isLogicalOperator(Filter filter) {
        return filter instanceof And || filter instanceof Or || filter instanceof Not;
    }

    private static String mapComparisonFilter(Filter filter) {
        if (filter instanceof IsEqualTo) return mapIsEqualTo((IsEqualTo) filter);
        if (filter instanceof IsNotEqualTo) return mapIsNotEqualTo((IsNotEqualTo) filter);
        if (filter instanceof IsGreaterThan) return mapIsGreaterThan((IsGreaterThan) filter);
        if (filter instanceof IsGreaterThanOrEqualTo) return mapIsGreaterThanOrEqualTo((IsGreaterThanOrEqualTo) filter);
        if (filter instanceof IsLessThan) return mapIsLessThan((IsLessThan) filter);
        if (filter instanceof IsLessThanOrEqualTo) return mapIsLessThanOrEqualTo((IsLessThanOrEqualTo) filter);
        if (filter instanceof IsIn) return mapIsIn((IsIn) filter);
        if (filter instanceof IsNotIn) return mapIsNotIn((IsNotIn) filter);
        throw new IllegalArgumentException("Unsupported filter: " + filter);
    }

    private static String getLogicalFormat(Filter filter) {
        if (filter instanceof And) return "(%s and %s)";
        if (filter instanceof Or) return "(%s or %s)";
        if (filter instanceof Not) return "(not %s)";
        throw new IllegalArgumentException("Unsupported filter: " + filter);
    }

    private static String getComparisonFormat(Filter filter) {
        if (filter instanceof IsEqualTo) return "k/value eq '%s'";
        if (filter instanceof IsNotEqualTo) return "k/value ne '%s'";
        if (filter instanceof IsGreaterThan) return "k/value gt '%s'";
        if (filter instanceof IsGreaterThanOrEqualTo) return "k/value ge '%s'";
        if (filter instanceof IsLessThan) return "k/value lt '%s'";
        if (filter instanceof IsLessThanOrEqualTo) return "k/value le '%s'";
        if (filter instanceof IsIn) return "search.in(k/value, ('%s'))";
        if (filter instanceof IsNotIn) return "not search.in(k/value, ('%s'))";
        throw new IllegalArgumentException("Unsupported filter: " + filter);
    }

    private static String mapIsNotIn(IsNotIn filter) {
        return formatComparisonFilter(filter.key(), mapSearchInValues(filter.comparisonValues()), getComparisonFormat(filter));
    }

    private static String mapIsIn(IsIn filter) {
        return formatComparisonFilter(filter.key(), mapSearchInValues(filter.comparisonValues()), getComparisonFormat(filter));
    }

    private static String mapIsLessThanOrEqualTo(IsLessThanOrEqualTo filter) {
        return formatComparisonFilter(filter.key(), filter.comparisonValue().toString(), getComparisonFormat(filter));
    }

    private static String mapIsLessThan(IsLessThan filter) {
        return formatComparisonFilter(filter.key(), filter.comparisonValue().toString(), getComparisonFormat(filter));
    }

    private static String mapIsGreaterThanOrEqualTo(IsGreaterThanOrEqualTo filter) {
        return formatComparisonFilter(filter.key(), filter.comparisonValue().toString(), getComparisonFormat(filter));
    }

    private static String mapIsGreaterThan(IsGreaterThan filter) {
        return formatComparisonFilter(filter.key(), filter.comparisonValue().toString(), getComparisonFormat(filter));
    }

    private static String mapIsEqualTo(IsEqualTo filter) {
        return formatComparisonFilter(filter.key(), filter.comparisonValue().toString(), getComparisonFormat(filter));
    }

    private static String mapIsNotEqualTo(IsNotEqualTo filter) {
        return formatComparisonFilter(filter.key(), filter.comparisonValue().toString(), getComparisonFormat(filter));
    }

    private static String mapSearchInValues(Collection<?> comparisonValues) {
        return comparisonValues.stream().map(Object::toString).sorted().collect(Collectors.joining(", "));
    }

    private static String formatComparisonFilter(String key, String value, String format) {
        return format("metadata/attributes/any(k: k/key eq '%s' and " + format + ")", key, value);
    }
}