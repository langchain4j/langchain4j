package dev.langchain4j.store.embedding.filter.builder.language;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;

import java.util.List;
import java.util.function.Function;

/**
 * A ContentRetriever delegate that wraps an EmbeddingStoreContentRetriever
 * and adds intelligent query parsing capabilities using LanguageModelFilterBuilder.
 * 
 * This delegate:
 * 1. Parses incoming queries to extract filters and limits
 * 2. Creates a clean query for embedding generation
 * 3. Uses the existing dynamicFilter and dynamicMaxResults mechanisms
 * 4. Preserves all other EmbeddingStoreContentRetriever functionality
 */
public class EmbeddingStoreContentRetrieverDelegate implements ContentRetriever {
    
    private final EmbeddingStoreContentRetriever delegate;
    private final LanguageModelJsonFilterBuilder filterBuilder;

    private EmbeddingStoreContentRetrieverDelegate(Builder builder) {
        if (builder.filterBuilder == null) {
            throw new IllegalArgumentException("LanguageModelJsonFilterBuilder is required");
        }
        this.filterBuilder = builder.filterBuilder;
        
        // Create the delegate with our dynamic functions
        this.delegate = EmbeddingStoreContentRetriever.builder()
            .embeddingStore(builder.embeddingStore)
            .embeddingModel(builder.embeddingModel)
            .maxResults(builder.maxResults)
            .minScore(builder.minScore)
            .dynamicFilter(createDynamicFilter())
            .build();
    }

    @Override
    public List<Content> retrieve(Query query) {
        // Process the query once to extract both filter and clean query
        FilterResult result;
        try {
            result = filterBuilder.buildFilterAndQuery(query.text());
        } catch (Exception e) {
            // If parsing fails, use the original query without filtering
            return delegate.retrieve(query);
        }
        
        // Create a special query that carries the processed result
        QueryWithFilterResult processedQuery = query.metadata() != null ? new QueryWithFilterResult(result.getModifiedQuery(), query.metadata(), result) : new QueryWithFilterResult(result.getModifiedQuery(), result);
        
        return delegate.retrieve(processedQuery);
    }

    private Function<Query, Filter> createDynamicFilter() {
        return query -> {
            // If it's our special query wrapper, use the cached result
            if (query instanceof QueryWithFilterResult) {
                return ((QueryWithFilterResult) query).getResult().getFilter();
            }
            return null;
        };
    }

    /**
     * Internal query wrapper to carry the processed FilterAndQueryResult
     */
    private static class QueryWithFilterResult extends Query {
        private final FilterResult result;
        
        public QueryWithFilterResult(String cleanText, FilterResult result) {
            super(cleanText);
            this.result = result;
        }

        public QueryWithFilterResult(String cleanText, Metadata metadata, FilterResult result) {
            super(cleanText, metadata);
            this.result = result;
        }
        
        public FilterResult getResult() {
            return result;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private EmbeddingStore<TextSegment> embeddingStore;
        private EmbeddingModel embeddingModel;
        private Integer maxResults;
        private Double minScore;
        private LanguageModelJsonFilterBuilder filterBuilder;

        public Builder embeddingStore(EmbeddingStore<TextSegment> embeddingStore) {
            this.embeddingStore = embeddingStore;
            return this;
        }

        public Builder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public Builder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public Builder minScore(Double minScore) {
            this.minScore = minScore;
            return this;
        }

        public Builder filterBuilder(LanguageModelJsonFilterBuilder filterBuilder) {
            this.filterBuilder = filterBuilder;
            return this;
        }

        public EmbeddingStoreContentRetrieverDelegate build() {
            if (embeddingStore == null) {
                throw new IllegalArgumentException("EmbeddingStore is required");
            }
            if (embeddingModel == null) {
                throw new IllegalArgumentException("EmbeddingModel is required");
            }
            if (filterBuilder == null) {
                throw new IllegalArgumentException("LanguageModelJsonFilterBuilder is required");
            }
            return new EmbeddingStoreContentRetrieverDelegate(this);
        }
    }
}