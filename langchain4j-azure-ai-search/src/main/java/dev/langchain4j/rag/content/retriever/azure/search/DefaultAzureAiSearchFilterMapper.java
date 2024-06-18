package dev.langchain4j.rag.content.retriever.azure.search;

import dev.langchain4j.store.embedding.filter.*;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

import java.util.Collection;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Maps {@link Filter} objects to Azure AI Search filter strings.
 * Use the default structure of the Azure AI Search Index.
 */
public class DefaultAzureAiSearchFilterMapper implements AzureAiSearchFilterMapper {

    public DefaultAzureAiSearchFilterMapper() {
    }

    public String map(Filter filter) {
        if (filter == null) return "";

        if (isLogicalOperator(filter)) {
            return mapLogicalOperator(filter);
        } else {
            return mapComparisonFilter(filter);
        }
    }
    
    
    private String mapLogicalOperator(Filter operator) {
        if (operator instanceof And) return  format(getLogicalFormat(operator), map(((And) operator).left()), map(((And) operator).right()));
        if (operator instanceof Or) return format(getLogicalFormat(operator), map(((Or) operator).left()), map(((Or) operator).right()));
        if (operator instanceof Not) return format(getLogicalFormat(operator), map(((Not) operator).expression()));
        throw new IllegalArgumentException("Unsupported operator: " + operator);
    }

    private boolean isLogicalOperator(Filter filter) {
        return filter instanceof And || filter instanceof Or || filter instanceof Not;
    }

    private String mapComparisonFilter(Filter filter) {
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

    private String getLogicalFormat(Filter filter) {
        if (filter instanceof And) return "(%s and %s)";
        if (filter instanceof Or) return "(%s or %s)";
        if (filter instanceof Not) return "(not %s)";
        throw new IllegalArgumentException("Unsupported filter: " + filter);
    }

    private String getComparisonFormat(Filter filter) {
        if (filter instanceof IsEqualTo) return "k/value eq '%s'";
// not use, it raplace by Not ( isEqualTo )
//        if (filter instanceof IsNotEqualTo) return "k/value ne '%s'";
        if (filter instanceof IsGreaterThan) return "k/value gt '%s'";
        if (filter instanceof IsGreaterThanOrEqualTo) return "k/value ge '%s'";
        if (filter instanceof IsLessThan) return "k/value lt '%s'";
        if (filter instanceof IsLessThanOrEqualTo) return "k/value le '%s'";
        if (filter instanceof IsIn) return "search.in(k/value, ('%s'))";
// not use, it raplace by Not ( IsIn )
//        if (filter instanceof IsNotIn) return "not search.in(k/value, ('%s'))";
        throw new IllegalArgumentException("Unsupported filter: " + filter);
    }

    private String mapIsNotIn(IsNotIn filter) {
        return map(Filter.not(new IsIn(filter.key(), filter.comparisonValues())));
    }

    private String mapIsIn(IsIn filter) {
        return formatComparisonFilter(filter.key(), mapSearchInValues(filter.comparisonValues()), getComparisonFormat(filter));
    }

    private String mapIsLessThanOrEqualTo(IsLessThanOrEqualTo filter) {
        return formatComparisonFilter(filter.key(), filter.comparisonValue().toString(), getComparisonFormat(filter));
    }

    private String mapIsLessThan(IsLessThan filter) {
        return formatComparisonFilter(filter.key(), filter.comparisonValue().toString(), getComparisonFormat(filter));
    }

    private String mapIsGreaterThanOrEqualTo(IsGreaterThanOrEqualTo filter) {
        return formatComparisonFilter(filter.key(), filter.comparisonValue().toString(), getComparisonFormat(filter));
    }

    private String mapIsGreaterThan(IsGreaterThan filter) {
        return formatComparisonFilter(filter.key(), filter.comparisonValue().toString(), getComparisonFormat(filter));
    }

    private String mapIsEqualTo(IsEqualTo filter) {
        return formatComparisonFilter(filter.key(), filter.comparisonValue().toString(), getComparisonFormat(filter));
    }

    private String mapIsNotEqualTo(IsNotEqualTo filter) {
        return map(Filter.not(new IsEqualTo(filter.key(), filter.comparisonValue())));
    }

    private String mapSearchInValues(Collection<?> comparisonValues) {
        return comparisonValues.stream().map(Object::toString).sorted().collect(Collectors.joining(", "));
    }

    private String formatComparisonFilter(String key, String value, String format) {
        return format("metadata/attributes/any(k: k/key eq '%s' and " + format + ")", key, value);
    }
}