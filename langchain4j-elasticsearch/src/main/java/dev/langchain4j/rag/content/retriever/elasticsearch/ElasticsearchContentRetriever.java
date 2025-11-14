package dev.langchain4j.rag.content.retriever.elasticsearch;

import java.util.ArrayList;
import java.util.List;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.elasticsearch.AbstractElasticsearchEmbeddingStore;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfiguration;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationFullText;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationHybrid;
import dev.langchain4j.store.embedding.elasticsearch.ElasticsearchConfigurationKnn;
import dev.langchain4j.store.embedding.filter.Filter;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an <a href="https://www.elastic.co/">Elasticsearch</a> index as a {@link ContentRetriever}.
 * TODO descriptions
 */
public class ElasticsearchContentRetriever extends AbstractElasticsearchEmbeddingStore implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchContentRetriever.class);
    private final EmbeddingModel embeddingModel;
    private final int maxResults;
    private final double minScore;
    private final Filter filter;

    /**
     * Creates an instance of ElasticsearchContentRetriever using a RestClient.
     *
     * @param configuration  Elasticsearch retriever configuration to use (knn, script, full text, hybrid, hybrid with reranker)
     * @param restClient     Elasticsearch Rest Client (mandatory)
     * @param indexName      Elasticsearch index name (optional). Default value: "default".
     *                       Index will be created automatically if not exists.
     * @param embeddingModel Embedding model to be used by the retriever
     * @param maxResults
     * @param minScore
     * @param filter
     */
    public ElasticsearchContentRetriever(ElasticsearchConfiguration configuration, RestClient restClient, String indexName, EmbeddingModel embeddingModel, final int maxResults, final double minScore, final Filter filter) {
        this.embeddingModel = embeddingModel;
        this.maxResults = maxResults;
        this.minScore = minScore;
        this.filter = filter;
        this.initialize(configuration, restClient, indexName, false);
    }

    /**
     * Creates an instance of ElasticsearchContentRetriever using a RestClient.
     *
     * @param configuration         Elasticsearch retriever configuration to use (knn, script, full text, hybrid, hybrid with reranker)
     * @param restClient            Elasticsearch Rest Client (mandatory)
     * @param indexName             Elasticsearch index name (optional). Default value: "default".
     *                              Index will be created automatically if not exists.
     * @param embeddingModel        Embedding model to be used by the retriever
     * @param includeVectorResponse If server version 9.2 or forward is used, this needs to be enabled to receive vector data as part of the response
     */
    public ElasticsearchContentRetriever(ElasticsearchConfiguration configuration, RestClient restClient, String indexName, EmbeddingModel embeddingModel, boolean includeVectorResponse, final int maxResults, final double minScore, final Filter filter) {
        this.embeddingModel = embeddingModel;
        this.maxResults = maxResults;
        this.minScore = minScore;
        this.filter = filter;
        this.initialize(configuration, restClient, indexName, includeVectorResponse);
    }

    @Override
    public List<Content> retrieve(final Query query) {
        if (configuration instanceof ElasticsearchConfigurationFullText) {
            // TODO check metadata
            return this.fullTextSearch(query.text()).stream().map(Content::from).toList();
        }
        Embedding referenceEmbedding = embeddingModel.embed(query.text()).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(referenceEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .filter(filter)
                .build();

        if (configuration instanceof ElasticsearchConfigurationHybrid) {
            return mapResultsToContentList(this.hybridSearch(request, query.text()));
        }

        return mapResultsToContentList(this.search(request));
    }

    // TODO
    private List<Content> mapResultsToContentList(EmbeddingSearchResult<TextSegment> searchResult) {
        List<Content> result = new ArrayList<>();
        return result;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private RestClient restClient;
        private String indexName = "default";
        private ElasticsearchConfiguration configuration = ElasticsearchConfigurationKnn.builder().build();
        private boolean includeVectorResponse = false;
        private EmbeddingModel embeddingModel;
        private int maxResults;
        private double minScore;
        private Filter filter;

        /**
         * @param restClient Elasticsearch RestClient (optional).
         *                   Effectively overrides all other connection parameters like serverUrl, etc.
         * @return builder
         */
        public Builder restClient(RestClient restClient) {
            this.restClient = restClient;
            return this;
        }

        /**
         * @param indexName Elasticsearch index name (optional). Default value: "default".
         * @return builder
         */
        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }


        /**
         * @param configuration the configuration to use
         * @return builder
         */
        public Builder configuration(ElasticsearchConfiguration configuration) {
            this.configuration = configuration;
            return this;
        }

        /**
         * @param includeVectorResponse If server version 9.2 or forward is used, this needs to be enabled to receive vector data as part of the response
         * @return builder
         */
        public Builder includeVectorResponse(boolean includeVectorResponse) {
            this.includeVectorResponse = includeVectorResponse;
            return this;
        }

        public Builder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public Builder maxResults(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public Builder minScore(double minScore) {
            this.minScore = minScore;
            return this;
        }

        public Builder filter(Filter filter) {
            this.filter = filter;
            return this;
        }

        public ElasticsearchContentRetriever build() {
            return new ElasticsearchContentRetriever(configuration, restClient, indexName, embeddingModel, includeVectorResponse, maxResults, minScore, filter);
        }
    }
}
