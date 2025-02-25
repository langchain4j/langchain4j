package dev.langchain4j.rag.content.retriever.azure.search;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.Collections.singletonList;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.core.util.Context;
import com.azure.search.documents.indexes.models.SearchIndex;
import com.azure.search.documents.models.*;
import com.azure.search.documents.util.SearchPagedIterable;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.azure.search.AbstractAzureAiSearchEmbeddingStore;
import dev.langchain4j.store.embedding.azure.search.AzureAiSearchRuntimeException;
import dev.langchain4j.store.embedding.azure.search.Document;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents Azure AI Search Service as a {@link ContentRetriever}.
 * <br>
 * This class supports 4 {@link AzureAiSearchQueryType}s:
 * <br>
 * - {@code VECTOR}: Uses the vector search algorithm to find the most similar {@link TextSegment}s.
 * More details can be found <a href="https://learn.microsoft.com/en-us/azure/search/vector-search-overview">here</a>.
 * <br>
 * - {@code FULL_TEXT}: Uses the full text search to find the most similar {@code TextSegment}s.
 * More details can be found  <a href="https://learn.microsoft.com/en-us/azure/search/search-lucene-query-architecture">here</a>.
 * <br>
 * - {@code HYBRID}: Uses the hybrid search (vector + full text) to find the most similar {@code TextSegment}s.
 * More details can be found  <a href="https://learn.microsoft.com/en-us/azure/search/hybrid-search-overview">here</a>.
 * <br>
 * - {@code HYBRID_WITH_RERANKING}: Uses the hybrid search (vector + full text) to find the most similar {@code TextSegment}s,
 * and uses the semantic re-ranker algorithm to rank the results.
 * More details can be found  <a href="https://learn.microsoft.com/en-us/azure/search/hybrid-search-ranking">here</a>.
 */
public class AzureAiSearchContentRetriever extends AbstractAzureAiSearchEmbeddingStore implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(AzureAiSearchContentRetriever.class);

    private final EmbeddingModel embeddingModel;

    private final AzureAiSearchQueryType azureAiSearchQueryType;

    private final int maxResults;

    private final double minScore;

    private final Filter filter;

    private final String searchFilter;

    public AzureAiSearchContentRetriever(
            String endpoint,
            AzureKeyCredential keyCredential,
            TokenCredential tokenCredential,
            boolean createOrUpdateIndex,
            int dimensions,
            SearchIndex index,
            String indexName,
            EmbeddingModel embeddingModel,
            int maxResults,
            double minScore,
            AzureAiSearchQueryType azureAiSearchQueryType,
            AzureAiSearchFilterMapper filterMapper,
            Filter filter) {
        ensureNotNull(endpoint, "endpoint");
        ensureTrue(
                (keyCredential != null && tokenCredential == null)
                        || (keyCredential == null && tokenCredential != null),
                "either keyCredential or tokenCredential must be set");

        if (AzureAiSearchQueryType.FULL_TEXT.equals(azureAiSearchQueryType)) {
            // Full-text search doesn't use embeddings, so dimensions must be 0
            ensureTrue(dimensions == 0, "for full-text search, dimensions must be 0");
        } else {
            ensureNotNull(embeddingModel, "embeddingModel");
            if (index == null) {
                ensureTrue(
                        dimensions >= 2 && dimensions <= 3072,
                        "dimensions must be set to a positive, non-zero integer between 2 and 3072");
            } else {
                ensureTrue(dimensions == 0, "for custom index, dimensions must be 0");
            }
        }
        if (keyCredential == null) {
            if (index == null) {
                this.initialize(
                        endpoint,
                        null,
                        tokenCredential,
                        createOrUpdateIndex,
                        dimensions,
                        null,
                        indexName,
                        filterMapper);
            } else {
                this.initialize(
                        endpoint, null, tokenCredential, createOrUpdateIndex, 0, index, indexName, filterMapper);
            }
        } else {
            if (index == null) {
                this.initialize(
                        endpoint, keyCredential, null, createOrUpdateIndex, dimensions, null, indexName, filterMapper);
            } else {
                this.initialize(endpoint, keyCredential, null, createOrUpdateIndex, 0, index, indexName, filterMapper);
            }
        }
        this.embeddingModel = embeddingModel;
        this.azureAiSearchQueryType = azureAiSearchQueryType;
        this.maxResults = maxResults;
        this.minScore = minScore;
        this.filter = filter;
        this.searchFilter = this.filterMapper.map(filter);
    }

    /**
     * Add content to the full text search engine.
     */
    public void add(String content) {
        add(singletonList(TextSegment.from(content)));
    }

    /**
     * Add {@code Document} to the full text search engine.
     */
    public void add(dev.langchain4j.data.document.Document document) {
        add(singletonList(document.toTextSegment()));
    }

    /**
     * Add {@code TextSegment} to the full text search engine.
     */
    public void add(TextSegment segment) {
        add(singletonList(segment));
    }

    /**
     * Add a list of {@code TextSegment}s to the full text search engine.
     */
    public void add(List<TextSegment> segments) {
        if (isNullOrEmpty(segments)) {
            log.info("Empty embeddings - no ops");
            return;
        }

        List<Document> documents = new ArrayList<>();
        for (TextSegment segment : segments) {
            Document document = new Document();
            document.setId(randomUUID());
            document.setContent(segment.text());
            Document.Metadata metadata = new Document.Metadata();
            metadata.setAttributes(segment.metadata());
            document.setMetadata(metadata);
            documents.add(document);
        }

        List<IndexingResult> indexingResults =
                searchClient.uploadDocuments(documents).getResults();
        for (IndexingResult indexingResult : indexingResults) {
            if (!indexingResult.isSucceeded()) {
                throw new AzureAiSearchRuntimeException("Failed to add content: " + indexingResult.getErrorMessage());
            } else {
                log.debug("Added content: {}", indexingResult.getKey());
            }
        }
    }

    @Override
    public List<Content> retrieve(Query query) {
        if (azureAiSearchQueryType == AzureAiSearchQueryType.VECTOR) {
            Embedding referenceEmbedding = embeddingModel.embed(query.text()).content();
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(referenceEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .filter(filter)
                    .build();

            List<EmbeddingMatch<TextSegment>> searchResult =
                    super.search(request).matches();
            return searchResult.stream()
                    .map(embeddingMatch -> Content.from(
                            embeddingMatch.embedded(),
                            Map.of(
                                    ContentMetadata.SCORE, embeddingMatch.score(),
                                    ContentMetadata.EMBEDDING_ID, embeddingMatch.embeddingId())))
                    .toList();
        } else if (azureAiSearchQueryType == AzureAiSearchQueryType.FULL_TEXT) {
            String content = query.text();
            return findRelevantWithFullText(content, maxResults, minScore);
        } else if (azureAiSearchQueryType == AzureAiSearchQueryType.HYBRID) {
            Embedding referenceEmbedding = embeddingModel.embed(query.text()).content();
            String content = query.text();
            return findRelevantWithHybrid(referenceEmbedding, content, maxResults, minScore);
        } else if (azureAiSearchQueryType == AzureAiSearchQueryType.HYBRID_WITH_RERANKING) {
            Embedding referenceEmbedding = embeddingModel.embed(query.text()).content();
            String content = query.text();
            return findRelevantWithHybridAndReranking(referenceEmbedding, content, maxResults, minScore);
        } else {
            throw new AzureAiSearchRuntimeException("Unknown Azure AI Search Query Type: " + azureAiSearchQueryType);
        }
    }

    private List<Content> findRelevantWithFullText(String content, int maxResults, double minScore) {
        SearchPagedIterable searchResults = searchClient.search(
                content, new SearchOptions().setTop(maxResults).setFilter(searchFilter), Context.NONE);

        return mapResultsToContentList(searchResults, AzureAiSearchQueryType.FULL_TEXT, minScore);
    }

    private List<Content> findRelevantWithHybrid(
            Embedding referenceEmbedding, String content, int maxResults, double minScore) {
        List<Float> vector = referenceEmbedding.vectorAsList();

        VectorizedQuery vectorizedQuery = new VectorizedQuery(vector)
                .setFields(DEFAULT_FIELD_CONTENT_VECTOR)
                .setKNearestNeighborsCount(maxResults);

        SearchPagedIterable searchResults = searchClient.search(
                content,
                new SearchOptions()
                        .setVectorSearchOptions(new VectorSearchOptions().setQueries(vectorizedQuery))
                        .setTop(maxResults)
                        .setFilter(searchFilter),
                Context.NONE);

        return mapResultsToContentList(searchResults, AzureAiSearchQueryType.HYBRID, minScore);
    }

    private List<Content> findRelevantWithHybridAndReranking(
            Embedding referenceEmbedding, String content, int maxResults, double minScore) {
        List<Float> vector = referenceEmbedding.vectorAsList();

        VectorizedQuery vectorizedQuery = new VectorizedQuery(vector)
                .setFields(DEFAULT_FIELD_CONTENT_VECTOR)
                .setKNearestNeighborsCount(maxResults);

        SearchPagedIterable searchResults = searchClient.search(
                content,
                new SearchOptions()
                        .setVectorSearchOptions(new VectorSearchOptions().setQueries(vectorizedQuery))
                        .setSemanticSearchOptions(
                                new SemanticSearchOptions().setSemanticConfigurationName(SEMANTIC_SEARCH_CONFIG_NAME))
                        .setQueryType(com.azure.search.documents.models.QueryType.SEMANTIC)
                        .setTop(maxResults)
                        .setFilter(searchFilter),
                Context.NONE);

        return mapResultsToContentList(searchResults, AzureAiSearchQueryType.HYBRID_WITH_RERANKING, minScore);
    }

    private List<Content> mapResultsToContentList(
            SearchPagedIterable searchResults, AzureAiSearchQueryType azureAiSearchQueryType, double minScore) {
        List<Content> result = new ArrayList<>();
        getEmbeddingMatches(searchResults, minScore, azureAiSearchQueryType).forEach(embeddingMatch -> {
            Content content = Content.from(
                    embeddingMatch.embedded(),
                    Map.of(
                            ContentMetadata.SCORE, embeddingMatch.score(),
                            ContentMetadata.EMBEDDING_ID, embeddingMatch.embeddingId()));
            result.add(content);
        });
        return result;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String endpoint;

        private AzureKeyCredential keyCredential;

        private TokenCredential tokenCredential;

        private boolean createOrUpdateIndex = true;

        private int dimensions;

        private SearchIndex index;

        private String indexName;

        private EmbeddingModel embeddingModel;

        private int maxResults = EmbeddingStoreContentRetriever.DEFAULT_MAX_RESULTS.apply(null);

        private double minScore = EmbeddingStoreContentRetriever.DEFAULT_MIN_SCORE.apply(null);

        private AzureAiSearchQueryType azureAiSearchQueryType;

        private Filter filter;

        private AzureAiSearchFilterMapper filterMapper;

        /**
         * Sets the Azure AI Search endpoint. This is a mandatory parameter.
         *
         * @param endpoint The Azure AI Search endpoint in the format: https://{resource}.search.windows.net
         * @return builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the Azure AI Search API key.
         *
         * @param apiKey The Azure AI Search API key.
         * @return builder
         */
        public Builder apiKey(String apiKey) {
            this.keyCredential = new AzureKeyCredential(apiKey);
            return this;
        }

        /**
         * Used to authenticate to Azure OpenAI with Azure Active Directory credentials.
         *
         * @param tokenCredential the credentials to authenticate with Azure Active Directory
         * @return builder
         */
        public Builder tokenCredential(TokenCredential tokenCredential) {
            this.tokenCredential = tokenCredential;
            return this;
        }

        /**
         * Whether to create or update the search index.
         *
         * @param createOrUpdateIndex Whether to create or update the index.
         * @return builder
         */
        public Builder createOrUpdateIndex(boolean createOrUpdateIndex) {
            this.createOrUpdateIndex = createOrUpdateIndex;
            return this;
        }

        /**
         * If using the ready-made index, sets the number of dimensions of the embeddings.
         * This parameter is exclusive of the index parameter.
         *
         * @param dimensions The number of dimensions of the embeddings.
         * @return builder
         */
        public Builder dimensions(int dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        /**
         * If using a custom index, sets the index to be used.
         * This parameter is exclusive of the dimensions parameter.
         *
         * @param index The index to be used.
         * @return builder
         */
        public Builder index(SearchIndex index) {
            this.index = index;
            return this;
        }

        /**
         * If no index is provided, set the name of the default index to be used.
         *
         * @param indexName The index name to be used.
         * @return builder
         */
        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        /**
         * Sets the Embedding Model.
         *
         * @param embeddingModel The Embedding Model.
         * @return builder
         */
        public Builder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        /**
         * Sets the maximum number of {@link Content}s to retrieve.
         *
         * @param maxResults The maximum number of {@link Content}s to retrieve.
         * @return builder
         */
        public Builder maxResults(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        /**
         * Sets the minimum relevance score for the returned {@link Content}s.
         * {@link Content}s scoring below {@code #minScore} are excluded from the results.
         *
         * @param minScore The minimum relevance score for the returned {@link Content}s.
         * @return builder
         */
        public Builder minScore(double minScore) {
            this.minScore = minScore;
            return this;
        }

        /**
         * Sets the Azure AI Search Query Type.
         *
         * @param azureAiSearchQueryType The Azure AI Search Query Type.
         * @return builder
         */
        public Builder queryType(AzureAiSearchQueryType azureAiSearchQueryType) {
            this.azureAiSearchQueryType = azureAiSearchQueryType;
            return this;
        }

        /**
         * Sets the filter to be applied to the search query.
         *
         * @param filter The filter to be applied to the search query.
         * @return builder
         */
        public Builder filter(Filter filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Sets the filter mapper to be used to map {@link Filter} objects to Azure AI Search filter strings.
         *
         * @param filterMapper The filter mapper to be used to map {@link Filter} objects to Azure AI Search filter strings.
         * @return builder
         */
        public Builder filterMapper(AzureAiSearchFilterMapper filterMapper) {
            this.filterMapper = filterMapper;
            return this;
        }

        public AzureAiSearchContentRetriever build() {
            return new AzureAiSearchContentRetriever(
                    endpoint,
                    keyCredential,
                    tokenCredential,
                    createOrUpdateIndex,
                    dimensions,
                    index,
                    indexName,
                    embeddingModel,
                    maxResults,
                    minScore,
                    azureAiSearchQueryType,
                    filterMapper,
                    filter);
        }
    }
}
