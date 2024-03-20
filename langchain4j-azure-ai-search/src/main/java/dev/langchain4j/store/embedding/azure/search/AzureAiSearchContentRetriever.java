package dev.langchain4j.store.embedding.azure.search;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.core.util.Context;
import com.azure.search.documents.SearchDocument;
import com.azure.search.documents.indexes.models.SearchIndex;
import com.azure.search.documents.models.*;
import com.azure.search.documents.util.SearchPagedIterable;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.stream.Collectors.toList;

/**
 * Azure AI Search EmbeddingStore Implementation.
 *
 * This class supports 4 {@link AzureAiSearchQueryType}s:
 * - Similarity: Uses the vector search algorithm to find the most similar embeddings.
 *   See https://learn.microsoft.com/en-us/azure/search/vector-search-overview for more information.
 * - Full Text: Uses the full text search to find the most similar embeddings.
 *   See https://learn.microsoft.com/en-us/azure/search/search-lucene-query-architecture for more information.
 * - Similarity Hybrid: Uses a hybrid search (vector + text) to find the most similar embeddings.
 *   See https://learn.microsoft.com/en-us/azure/search/hybrid-search-overview for more information.
 * - Semantic Hybrid: Uses a hybrid search (vector + text) to find the most similar embeddings, and uses the semantic re-ranker algorithm to rank the results.
 *   See https://learn.microsoft.com/en-us/azure/search/hybrid-search-ranking for more information.
 */
public class AzureAiSearchContentRetriever extends AbstractAzureAiSearchEmbeddingStore implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(AzureAiSearchContentRetriever.class);

    private EmbeddingModel embeddingModel;

    private AzureAiSearchQueryType azureAiSearchQueryType;

    private int maxResults;
    private double minScore;

    public AzureAiSearchContentRetriever(String endpoint, AzureKeyCredential keyCredential, int dimensions, EmbeddingModel embeddingModel, int maxResults, double minScore, AzureAiSearchQueryType azureAiSearchQueryType) {
        this.initialize(endpoint, keyCredential, null, dimensions, null);
        this.embeddingModel = embeddingModel;
        this.azureAiSearchQueryType = azureAiSearchQueryType;
        this.maxResults = maxResults;
        this.minScore = minScore;
    }

    public AzureAiSearchContentRetriever(String endpoint, AzureKeyCredential keyCredential, SearchIndex index, EmbeddingModel embeddingModel, int maxResults, double minScore, AzureAiSearchQueryType azureAiSearchQueryType) {
        this.initialize(endpoint, keyCredential, null, 0, index);
        this.embeddingModel = embeddingModel;
        this.azureAiSearchQueryType = azureAiSearchQueryType;
        this.maxResults = maxResults;
        this.minScore = minScore;
    }

    public AzureAiSearchContentRetriever(String endpoint, TokenCredential tokenCredential, int dimensions, EmbeddingModel embeddingModel, int maxResults, double minScore, AzureAiSearchQueryType azureAiSearchQueryType) {
        this.initialize(endpoint, null, tokenCredential, dimensions, null);
        this.embeddingModel = embeddingModel;
        this.azureAiSearchQueryType = azureAiSearchQueryType;
        this.maxResults = maxResults;
        this.minScore = minScore;
    }

    public AzureAiSearchContentRetriever(String endpoint, TokenCredential tokenCredential, SearchIndex index, EmbeddingModel embeddingModel, int maxResults, double minScore, AzureAiSearchQueryType azureAiSearchQueryType) {
        this.initialize(endpoint, null, tokenCredential, 0, index);
        this.embeddingModel = embeddingModel;
        this.azureAiSearchQueryType = azureAiSearchQueryType;
        this.maxResults = maxResults;
        this.minScore = minScore;
    }

    @Override
    public List<Content> retrieve(Query query) {
        if (azureAiSearchQueryType == AzureAiSearchQueryType.VECTOR) {
            Embedding referenceEmbedding = embeddingModel.embed(query.text()).content();
            List<EmbeddingMatch<TextSegment>> searchResult = super.findRelevant(referenceEmbedding, maxResults, minScore);
            return searchResult.stream()
                    .map(EmbeddingMatch::embedded)
                    .map(Content::from)
                    .collect(toList());

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

    List<Content> findRelevantWithFullText(String content, int maxResults, double minScore) {
        SearchPagedIterable searchResults =
                searchClient.search(content,
                        new SearchOptions()
                                .setTop(maxResults),
                        Context.NONE);

        return mapResultsToContentList(searchResults, AzureAiSearchQueryType.FULL_TEXT, minScore);
    }

    List<Content> findRelevantWithHybrid(Embedding referenceEmbedding, String content, int maxResults, double minScore) {
        List<Float> vector = referenceEmbedding.vectorAsList();

        VectorizedQuery vectorizedQuery = new VectorizedQuery(vector)
                .setFields(DEFAULT_FIELD_CONTENT_VECTOR)
                .setKNearestNeighborsCount(maxResults);

        SearchPagedIterable searchResults =
                searchClient.search(content,
                        new SearchOptions()
                                .setVectorSearchOptions(new VectorSearchOptions().setQueries(vectorizedQuery))
                                .setTop(maxResults),
                        Context.NONE);

        return mapResultsToContentList(searchResults, AzureAiSearchQueryType.HYBRID, minScore);
    }

    List<Content> findRelevantWithHybridAndReranking(Embedding referenceEmbedding, String content, int maxResults, double minScore) {
        List<Float> vector = referenceEmbedding.vectorAsList();

        VectorizedQuery vectorizedQuery = new VectorizedQuery(vector)
                .setFields(DEFAULT_FIELD_CONTENT_VECTOR)
                .setKNearestNeighborsCount(maxResults);

        SearchPagedIterable searchResults =
                searchClient.search(content,
                        new SearchOptions()
                                .setVectorSearchOptions(new VectorSearchOptions().setQueries(vectorizedQuery))
                                .setSemanticSearchOptions(new SemanticSearchOptions().setSemanticConfigurationName(SEMANTIC_SEARCH_CONFIG_NAME))
                                .setQueryType(com.azure.search.documents.models.QueryType.SEMANTIC)
                                .setTop(maxResults),
                        Context.NONE);

        return mapResultsToContentList(searchResults, AzureAiSearchQueryType.HYBRID_WITH_RERANKING, minScore);
    }

    private List<Content> mapResultsToContentList(SearchPagedIterable searchResults, AzureAiSearchQueryType azureAiSearchQueryType, double minScore) {
        List<Content> result = new ArrayList<>();
        for (SearchResult searchResult : searchResults) {
            double score = fromAzureScoreToRelevanceScore(searchResult, azureAiSearchQueryType);
            if (score < minScore) {
                continue;
            }
            SearchDocument searchDocument = searchResult.getDocument(SearchDocument.class);
            String embeddedContent = (String) searchDocument.get(DEFAULT_FIELD_CONTENT);
            Content content = Content.from(embeddedContent);
            result.add(content);
        }
        return result;
    }

    /**
     * Calculates LangChain4j's RelevanceScore from Azure AI Search's score, for the 4 types of search.
     */
    static double fromAzureScoreToRelevanceScore(SearchResult searchResult, AzureAiSearchQueryType azureAiSearchQueryType) {
        if (azureAiSearchQueryType == AzureAiSearchQueryType.VECTOR) {
            // Calculates LangChain4j's RelevanceScore from Azure AI Search's score.

           //  Score in Azure AI Search is transformed into a cosine similarity as described here:
           // https://learn.microsoft.com/en-us/azure/search/vector-search-ranking#scores-in-a-vector-search-results

           // RelevanceScore in LangChain4j is a derivative of cosine similarity,
           // but it compresses it into 0..1 range (instead of -1..1) for ease of use.
            double score = searchResult.getScore();
            return AbstractAzureAiSearchEmbeddingStore.fromAzureScoreToRelevanceScore(score);
        } else if (azureAiSearchQueryType == AzureAiSearchQueryType.FULL_TEXT) {
            // Search score is into 0..1 range already
            return searchResult.getScore();
        } else if (azureAiSearchQueryType == AzureAiSearchQueryType.HYBRID) {
            // Search score is into 0..1 range already
            return searchResult.getScore();
        } else if (azureAiSearchQueryType == AzureAiSearchQueryType.HYBRID_WITH_RERANKING) {
            // Re-ranker score is into 0..4 range, so we need to divide the re-reranker score by 4 to fit in the 0..1 range.
            // The re-ranker score is a separate result from the original search score.
            // See https://azuresdkdocs.blob.core.windows.net/$web/java/azure-search-documents/11.6.2/com/azure/search/documents/models/SearchResult.html#getSemanticSearch()
            return searchResult.getSemanticSearch().getRerankerScore() / 4.0;
        } else {
            throw new AzureAiSearchRuntimeException("Unknown Azure AI Search Query Type: " + azureAiSearchQueryType);
        }
    }

    public static AzureAiSearchContentRetrieverBuilder builder() {
        return new AzureAiSearchContentRetrieverBuilder();
    }

    public static class AzureAiSearchContentRetrieverBuilder {

        private String endpoint;

        private AzureKeyCredential keyCredential;

        private TokenCredential tokenCredential;

        private int dimensions;

        private SearchIndex index;

        private EmbeddingModel embeddingModel;

        private int maxResults = EmbeddingStoreContentRetriever.DEFAULT_MAX_RESULTS.apply(null);

        private double minScore = EmbeddingStoreContentRetriever.DEFAULT_MIN_SCORE.apply(null);

        private AzureAiSearchQueryType azureAiSearchQueryType;

        /**
         * Sets the Azure AI Search endpoint. This is a mandatory parameter.
         *
         * @param endpoint The Azure AI Search endpoint in the format: https://{resource}.search.windows.net
         * @return builder
         */
        public AzureAiSearchContentRetrieverBuilder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Sets the Azure AI Search API key.
         *
         * @param apiKey The Azure AI Search API key.
         * @return builder
         */
        public AzureAiSearchContentRetrieverBuilder apiKey(String apiKey) {
            this.keyCredential = new AzureKeyCredential(apiKey);
            return this;
        }

        /**
         * Used to authenticate to Azure OpenAI with Azure Active Directory credentials.
         * @param tokenCredential the credentials to authenticate with Azure Active Directory
         * @return builder
         */
        public AzureAiSearchContentRetrieverBuilder tokenCredential(TokenCredential tokenCredential) {
            this.tokenCredential = tokenCredential;
            return this;
        }

        /**
         * If using the ready-made index, sets the number of dimensions of the embeddings.
         * This parameter is exclusive of the index parameter.
         *
         * @param dimensions The number of dimensions of the embeddings.
         * @return builder
         */
        public AzureAiSearchContentRetrieverBuilder dimensions(int dimensions) {
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
        public AzureAiSearchContentRetrieverBuilder index(SearchIndex index) {
            this.index = index;
            return this;
        }

        /**
         * Sets the Embedding Model.
         *
         * @param embeddingModel The Embedding Model.
         * @return builder
         */
        public AzureAiSearchContentRetrieverBuilder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        /**
         * Sets the maximum number of {@link Content}s to retrieve.
         *
         * @param maxResults The maximum number of {@link Content}s to retrieve.
         * @return builder
         */
        public AzureAiSearchContentRetrieverBuilder maxResults(int maxResults) {
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
        public AzureAiSearchContentRetrieverBuilder minScore(double minScore) {
            this.minScore = minScore;
            return this;
        }

        /**
         * Sets the Azure AI Search Query Type.
         *
         * @param azureAiSearchQueryType The Azure AI Search Query Type.
         * @return builder
         */
        public AzureAiSearchContentRetrieverBuilder queryType(AzureAiSearchQueryType azureAiSearchQueryType) {
            this.azureAiSearchQueryType = azureAiSearchQueryType;
            return this;
        }

        public AzureAiSearchContentRetriever build() {
            ensureNotNull(endpoint, "endpoint");
            ensureTrue(keyCredential != null || tokenCredential != null, "either apiKey or tokenCredential must be set");
            ensureTrue(dimensions > 0 || index != null, "either dimensions or index must be set");
            if (!AzureAiSearchQueryType.FULL_TEXT.equals(azureAiSearchQueryType)) {
                ensureNotNull(embeddingModel, "embeddingModel");
            }
            if (keyCredential == null) {
                if (index == null) {
                    return new AzureAiSearchContentRetriever(endpoint, tokenCredential, dimensions, embeddingModel, maxResults, minScore, azureAiSearchQueryType);
                } else {
                    return new AzureAiSearchContentRetriever(endpoint, tokenCredential, index, embeddingModel, maxResults, minScore, azureAiSearchQueryType);
                }
            } else {
                if (index == null) {
                    return new AzureAiSearchContentRetriever(endpoint, keyCredential, dimensions, embeddingModel, maxResults, minScore, azureAiSearchQueryType);
                } else {
                    return new AzureAiSearchContentRetriever(endpoint, keyCredential, index, embeddingModel, maxResults, minScore, azureAiSearchQueryType);
                }
            }
        }
    }
}
