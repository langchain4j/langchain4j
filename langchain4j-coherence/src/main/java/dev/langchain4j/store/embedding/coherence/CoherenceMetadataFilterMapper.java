package dev.langchain4j.store.embedding.coherence;

import com.oracle.coherence.ai.DocumentChunk;
import com.tangosol.util.Extractors;
import com.tangosol.util.Filters;
import com.tangosol.util.ValueExtractor;
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

import java.util.Collection;
import java.util.HashSet;

/**
 * A mapper that maps {@link Filter LangChain filters} to
 * {@link com.tangosol.util.Filter Coherence filters} that
 * can be applied to {@link DocumentChunk} metadata.
 */
class CoherenceMetadataFilterMapper
    {
    /**
     * Return the Coherence filter that is equivalent to the
     * specified LangChain filter.
     *
     * @param filter  the LangChain filter to convert
     *
     * @return the equivalent Coherence filter
     */
    static com.tangosol.util.Filter<DocumentChunk> map(Filter filter) {
        if (filter == null) {
            return null;
        } else if (filter instanceof IsEqualTo) {
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

    private static <V> ValueExtractor<DocumentChunk, V> extractor(String field) {
        return Extractors.chained(ValueExtractor.of(DocumentChunk::metadata), Extractors.extract(field));
    }

    private static com.tangosol.util.Filter<DocumentChunk> mapEqual(IsEqualTo isEqualTo) {
        return Filters.equal(extractor(isEqualTo.key()), isEqualTo.comparisonValue());
    }

    private static com.tangosol.util.Filter<DocumentChunk> mapNotEqual(IsNotEqualTo isNotEqualTo) {
        return Filters.not(Filters.equal(extractor(isNotEqualTo.key()), isNotEqualTo.comparisonValue()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static com.tangosol.util.Filter<DocumentChunk> mapGreaterThan(IsGreaterThan isGreaterThan) {
        ValueExtractor<DocumentChunk, ? extends Comparable> extractor = extractor(isGreaterThan.key());
        Comparable value = isGreaterThan.comparisonValue();
        return Filters.greater(extractor, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static com.tangosol.util.Filter<DocumentChunk> mapGreaterThanOrEqual(IsGreaterThanOrEqualTo isGreaterThanOrEqualTo) {
        ValueExtractor<DocumentChunk, ? extends Comparable> extractor = extractor(isGreaterThanOrEqualTo.key());
        Comparable value = isGreaterThanOrEqualTo.comparisonValue();
        return Filters.greaterEqual(extractor, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static com.tangosol.util.Filter<DocumentChunk> mapLessThan(IsLessThan isLessThan) {
        ValueExtractor<DocumentChunk, ? extends Comparable> extractor = extractor(isLessThan.key());
        Comparable value = isLessThan.comparisonValue();
        return Filters.less(extractor, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static com.tangosol.util.Filter<DocumentChunk> mapLessThanOrEqual(IsLessThanOrEqualTo isLessThanOrEqualTo) {
        ValueExtractor<DocumentChunk, ? extends Comparable> extractor = extractor(isLessThanOrEqualTo.key());
        Comparable value = isLessThanOrEqualTo.comparisonValue();
        return Filters.lessEqual(extractor, value);
    }

    public static com.tangosol.util.Filter<DocumentChunk> mapIn(IsIn isIn) {
        ValueExtractor<DocumentChunk, ?> extractor = extractor(isIn.key());
        Collection<?> values = isIn.comparisonValues();
        return Filters.in(extractor, new HashSet<>(values));
    }

    public static com.tangosol.util.Filter<DocumentChunk> mapNotIn(IsNotIn isNotIn) {
        ValueExtractor<DocumentChunk, ?> extractor = extractor(isNotIn.key());
        Collection<?> values = isNotIn.comparisonValues();
        return Filters.not(Filters.in(extractor, new HashSet<>(values)));
    }

    @SuppressWarnings("unchecked")
    private static com.tangosol.util.Filter<DocumentChunk> mapAnd(And and) {
        return Filters.all(map(and.left()), map(and.right()));
    }

    private static com.tangosol.util.Filter<DocumentChunk> mapNot(Not not) {
        return Filters.not(map(not.expression()));
    }

    @SuppressWarnings("unchecked")
    private static com.tangosol.util.Filter<DocumentChunk> mapOr(Or or) {
        return Filters.any(map(or.left()), map(or.right()));
    }
}

