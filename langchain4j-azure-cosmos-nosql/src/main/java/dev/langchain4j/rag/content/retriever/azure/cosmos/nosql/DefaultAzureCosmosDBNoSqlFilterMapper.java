package dev.langchain4j.rag.content.retriever.azure.cosmos.nosql;

import static java.lang.String.format;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.ContainsString;
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
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Maps {@link Filter} objects to Azure Cosmos DB NoSQL filter strings.
 * Supports standard comparison operators and full-text search functions.
 */
public class DefaultAzureCosmosDBNoSqlFilterMapper implements AzureCosmosDBNoSqlFilterMapper {

    public DefaultAzureCosmosDBNoSqlFilterMapper() {}

    @Override
    public String map(Filter filter) {
        if (filter == null) return "";

        if (isLogicalOperator(filter)) {
            return mapLogicalOperator(filter);
        } else {
            return mapComparisonFilter(filter);
        }
    }

    private String mapLogicalOperator(Filter operator) {
        if (operator instanceof And) {
            return format(getLogicalFormat(operator), map(((And) operator).left()), map(((And) operator).right()));
        }
        if (operator instanceof Or) {
            return format(getLogicalFormat(operator), map(((Or) operator).left()), map(((Or) operator).right()));
        }
        if (operator instanceof Not) {
            return format(getLogicalFormat(operator), map(((Not) operator).expression()));
        }
        throw new UnsupportedOperationException(
                "Unsupported filter type: " + operator.getClass().getName());
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
        if (filter instanceof ContainsString) return mapContainsString((ContainsString) filter);

        // Full-text search operators
        if (filter instanceof FullTextContains) return mapFullTextContains((FullTextContains) filter);
        if (filter instanceof FullTextContainsAll) return mapFullTextContainsAll((FullTextContainsAll) filter);
        if (filter instanceof FullTextContainsAny) return mapFullTextContainsAny((FullTextContainsAny) filter);

        throw new UnsupportedOperationException(
                "Unsupported filter type: " + filter.getClass().getName());
    }

    private String getLogicalFormat(Filter filter) {
        if (filter instanceof And) return "(%s AND %s)";
        if (filter instanceof Or) return "(%s OR %s)";
        if (filter instanceof Not) return "(NOT %s)";
        throw new UnsupportedOperationException(
                "Unsupported filter type: " + filter.getClass().getName());
    }

    private String getComparisonFormat(Filter filter) {
        if (filter instanceof IsEqualTo) return "c.%s = %s";
        if (filter instanceof IsGreaterThan) return "c.%s > %s";
        if (filter instanceof IsGreaterThanOrEqualTo) return "c.%s >= %s";
        if (filter instanceof IsLessThan) return "c.%s < %s";
        if (filter instanceof IsLessThanOrEqualTo) return "c.%s <= %s";
        if (filter instanceof ContainsString) return "CONTAINS(c.%s, %s)";
        throw new UnsupportedOperationException(
                "Unsupported filter type: " + filter.getClass().getName());
    }

    private String mapIsEqualTo(IsEqualTo filter) {
        return format(getComparisonFormat(filter), filter.key(), formatValue(filter.comparisonValue()));
    }

    private String mapIsNotEqualTo(IsNotEqualTo filter) {
        return map(Filter.not(new IsEqualTo(filter.key(), filter.comparisonValue())));
    }

    private String mapIsGreaterThan(IsGreaterThan filter) {
        return format(getComparisonFormat(filter), filter.key(), formatValue(filter.comparisonValue()));
    }

    private String mapIsGreaterThanOrEqualTo(IsGreaterThanOrEqualTo filter) {
        return format(getComparisonFormat(filter), filter.key(), formatValue(filter.comparisonValue()));
    }

    private String mapIsLessThan(IsLessThan filter) {
        return format(getComparisonFormat(filter), filter.key(), formatValue(filter.comparisonValue()));
    }

    private String mapIsLessThanOrEqualTo(IsLessThanOrEqualTo filter) {
        return format(getComparisonFormat(filter), filter.key(), formatValue(filter.comparisonValue()));
    }

    private String mapIsIn(IsIn filter) {
        String values = mapInValues(filter.comparisonValues());
        return format("c.%s IN (%s)", filter.key(), values);
    }

    private String mapIsNotIn(IsNotIn filter) {
        return map(Filter.not(new IsIn(filter.key(), filter.comparisonValues())));
    }

    private String mapContainsString(ContainsString filter) {
        return format(getComparisonFormat(filter), filter.key(), formatValue(filter.comparisonValue()));
    }

    // Full-text search mappings
    private String mapFullTextContains(FullTextContains filter) {
        return format("FullTextContains(c.%s, %s)", filter.key(), formatValue(filter.searchTerm()));
    }

    private String mapFullTextContainsAll(FullTextContainsAll filter) {
        String terms = filter.searchTerms().stream().map(this::formatValue).collect(Collectors.joining(", "));
        return format("FullTextContainsAll(c.%s, %s)", filter.key(), terms);
    }

    private String mapFullTextContainsAny(FullTextContainsAny filter) {
        String terms = filter.searchTerms().stream().map(this::formatValue).collect(Collectors.joining(", "));
        return format("FullTextContainsAny(c.%s, %s)", filter.key(), terms);
    }

    private String mapInValues(Collection<?> comparisonValues) {
        return comparisonValues.stream().map(this::formatValue).sorted().collect(Collectors.joining(", "));
    }

    private String formatValue(Object value) {
        if (value instanceof String) {
            return "\"" + value + "\"";
        }
        return value.toString();
    }
}
