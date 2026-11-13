package dev.langchain4j.rag.content.retriever.azure.cosmos.nosql;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maps {@link Filter} objects to Azure Cosmos DB NoSQL filter strings.
 * Supports standard comparison operators and full-text search functions.
 * <p>
 * Standard comparison filters address entries in the document's {@code metadata} object, so the key
 * {@code category} produces {@code c["metadata"]["category"]}. Metadata keys are literal property names,
 * including dots, spaces, punctuation, and Cosmos DB reserved words. For backward compatibility, a key
 * starting with {@code metadata.} is still interpreted as a dotted document path, so
 * {@code metadata.category} also produces {@code c["metadata"]["category"]}.
 * <p>
 * Full-text filters address document properties instead: {@code new FullTextContains("text", "word")}
 * searches {@code c["text"]}. Dots in full-text keys separate document path segments. Full-text and
 * metadata filters can be combined with logical operators.
 * <p>
 * This integration stores metadata values as strings. Numeric comparisons also use
 * {@code StringToNumber}, so they work numerically with existing string-backed metadata as well as
 * native JSON numbers. String comparisons remain lexicographic. Missing metadata entries do not match
 * positive comparisons, but do match their negations, including {@link IsNotEqualTo} and {@link IsNotIn}.
 * <p>
 * Keys and values are both escaped, so neither can end the quoted text it sits in and add query syntax
 * of its own. A key that arrived from untrusted input and looks like a fragment of a query is therefore
 * treated as an ordinary property name, and cannot alter the query structure. Whether it matches a
 * document depends on the document's properties and the filter operator.
 * <p>
 * Comparison values support {@link String}, {@link UUID} (as a quoted string), {@link Boolean},
 * {@link Byte}, {@link Short}, {@link Integer}, {@link Long}, finite {@link Float} and {@link Double},
 * and exact {@link BigInteger} and {@link BigDecimal} instances (not subclasses). Unsupported types,
 * null values, and non-finite numbers cause an {@link IllegalArgumentException}. This also applies to
 * each element of an {@link IsIn} or {@link IsNotIn} filter.
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
        if (filter instanceof IsEqualTo) return "%s = %s";
        if (filter instanceof IsGreaterThan) return "%s > %s";
        if (filter instanceof IsGreaterThanOrEqualTo) return "%s >= %s";
        if (filter instanceof IsLessThan) return "%s < %s";
        if (filter instanceof IsLessThanOrEqualTo) return "%s <= %s";
        if (filter instanceof ContainsString) return "CONTAINS(%s, %s)";
        throw new UnsupportedOperationException(
                "Unsupported filter type: " + filter.getClass().getName());
    }

    private String mapIsEqualTo(IsEqualTo filter) {
        return mapComparison(filter.key(), filter.comparisonValue(), getComparisonFormat(filter));
    }

    private String mapIsNotEqualTo(IsNotEqualTo filter) {
        return map(Filter.not(new IsEqualTo(filter.key(), filter.comparisonValue())));
    }

    private String mapIsGreaterThan(IsGreaterThan filter) {
        return mapComparison(filter.key(), filter.comparisonValue(), getComparisonFormat(filter));
    }

    private String mapIsGreaterThanOrEqualTo(IsGreaterThanOrEqualTo filter) {
        return mapComparison(filter.key(), filter.comparisonValue(), getComparisonFormat(filter));
    }

    private String mapIsLessThan(IsLessThan filter) {
        return mapComparison(filter.key(), filter.comparisonValue(), getComparisonFormat(filter));
    }

    private String mapIsLessThanOrEqualTo(IsLessThanOrEqualTo filter) {
        return mapComparison(filter.key(), filter.comparisonValue(), getComparisonFormat(filter));
    }

    private String mapIsIn(IsIn filter) {
        String values = mapInValues(filter.comparisonValues());
        return formatMetadataComparison(
                filter.key(),
                "%s IN (%s)",
                values,
                filter.comparisonValues().stream().anyMatch(Number.class::isInstance));
    }

    private String mapIsNotIn(IsNotIn filter) {
        return map(Filter.not(new IsIn(filter.key(), filter.comparisonValues())));
    }

    private String mapContainsString(ContainsString filter) {
        return mapComparison(filter.key(), filter.comparisonValue(), getComparisonFormat(filter));
    }

    private String mapComparison(String key, Object value, String comparisonFormat) {
        return formatMetadataComparison(key, comparisonFormat, formatValue(value), value instanceof Number);
    }

    private String formatMetadataComparison(
            String key, String comparisonFormat, String value, boolean containsNumbers) {
        String metadataKey = formatMetadataKey(key);
        String comparison = format(comparisonFormat, metadataKey, value);
        if (containsNumbers) {
            comparison += " OR " + format(comparisonFormat, "StringToNumber(" + metadataKey + ")", value);
        }
        // Cosmos comparisons can return undefined; negating a missing metadata entry must return true.
        return format("((%s) ?? false)", comparison);
    }

    // Full-text search mappings
    private String mapFullTextContains(FullTextContains filter) {
        return format("FullTextContains(%s, %s)", formatKey(filter.key()), formatValue(filter.searchTerm()));
    }

    private String mapFullTextContainsAll(FullTextContainsAll filter) {
        String terms = filter.searchTerms().stream().map(this::formatValue).collect(Collectors.joining(", "));
        return format("FullTextContainsAll(%s, %s)", formatKey(filter.key()), terms);
    }

    private String mapFullTextContainsAny(FullTextContainsAny filter) {
        String terms = filter.searchTerms().stream().map(this::formatValue).collect(Collectors.joining(", "));
        return format("FullTextContainsAny(%s, %s)", formatKey(filter.key()), terms);
    }

    private String mapInValues(Collection<?> comparisonValues) {
        return comparisonValues.stream().map(this::formatValue).sorted().collect(Collectors.joining(", "));
    }

    private String formatMetadataKey(String key) {
        if (key.startsWith("metadata.")) {
            return formatKey(key);
        }
        return "c[\"metadata\"][\"" + escape(key) + "\"]";
    }

    private String formatKey(String key) {
        StringBuilder accessor = new StringBuilder("c");
        for (String segment : key.split("\\.", -1)) {
            accessor.append("[\"").append(escape(segment)).append("\"]");
        }
        return accessor.toString();
    }

    private String formatValue(Object value) {
        ensureNotNull(value, "comparisonValue");
        if (value instanceof String || value instanceof UUID) {
            return "\"" + escape(value.toString()) + "\"";
        }
        if ((value instanceof Float && !Float.isFinite((Float) value))
                || (value instanceof Double && !Double.isFinite((Double) value))) {
            throw new IllegalArgumentException("Comparison value must be a finite number");
        }
        // Only trusted scalar implementations may supply unquoted SQL literals.
        // BigInteger and BigDecimal are not final, so subclasses must not reach toString().
        if (value instanceof Boolean
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value.getClass() == BigInteger.class
                || value.getClass() == BigDecimal.class) {
            return value.toString();
        }
        throw new IllegalArgumentException(
                "Unsupported comparison value type: " + value.getClass().getName());
    }

    /**
     * Escapes a string that is embedded into a double-quoted Cosmos DB string literal, whether it is a
     * metadata key or a value. Cosmos DB string literals follow the JSON grammar, so the quote and the
     * escape character have to be escaped, and a control character cannot appear raw.
     */
    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '\\' || character == '"') {
                escaped.append('\\').append(character);
            } else if (character < 0x20) {
                escaped.append(String.format("\\u%04x", (int) character));
            } else {
                escaped.append(character);
            }
        }
        return escaped.toString();
    }
}
