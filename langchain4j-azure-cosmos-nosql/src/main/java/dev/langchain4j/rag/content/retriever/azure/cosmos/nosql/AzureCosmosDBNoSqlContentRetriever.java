package dev.langchain4j.rag.content.retriever.azure.cosmos.nosql;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.cosmos.models.CosmosFullTextPolicy;
import com.azure.cosmos.models.CosmosVectorEmbeddingPolicy;
import com.azure.cosmos.models.IndexingPolicy;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.azure.cosmos.nosql.AbstractAzureCosmosDBNoSqlEmbeddingStore;
import dev.langchain4j.store.embedding.azure.cosmos.nosql.AzureCosmosDBNoSqlRuntimeException;
import dev.langchain4j.store.embedding.azure.cosmos.nosql.AzureCosmosDBSearchQueryType;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.List;
import java.util.Map;

public class AzureCosmosDBNoSqlContentRetriever extends AbstractAzureCosmosDBNoSqlEmbeddingStore
        implements ContentRetriever {

    private final EmbeddingModel embeddingModel;
    private final AzureCosmosDBSearchQueryType azureCosmosDBSearchQueryType;
    private final int maxResults;
    private final double minScore;
    private final Filter filter;

    public AzureCosmosDBNoSqlContentRetriever(Builder builder) {
        ensureNotNull(builder.endpoint, "endpoint");
        ensureTrue(
                (builder.keyCredential != null && builder.tokenCredential == null)
                        || (builder.keyCredential == null && builder.tokenCredential != null),
                "either keyCredential or tokenCredential must be set");

        if (builder.keyCredential != null) {
            this.initialize(
                    builder.endpoint,
                    builder.keyCredential,
                    null,
                    builder.databaseName,
                    builder.containerName,
                    builder.partitionKeyPath,
                    builder.indexingPolicy,
                    builder.cosmosVectorEmbeddingPolicy,
                    builder.cosmosFullTextPolicy,
                    builder.vectorStoreThroughput,
                    builder.azureCosmosDBSearchQueryType,
                    null);
        } else {
            this.initialize(
                    builder.endpoint,
                    null,
                    builder.tokenCredential,
                    builder.databaseName,
                    builder.containerName,
                    builder.partitionKeyPath,
                    builder.indexingPolicy,
                    builder.cosmosVectorEmbeddingPolicy,
                    builder.cosmosFullTextPolicy,
                    builder.vectorStoreThroughput,
                    builder.azureCosmosDBSearchQueryType,
                    null);
        }

        this.embeddingModel = builder.embeddingModel;
        this.azureCosmosDBSearchQueryType = builder.azureCosmosDBSearchQueryType;
        this.maxResults = builder.maxResults;
        this.minScore = builder.minScore;
        this.filter = builder.filter;
    }

    @Deprecated(forRemoval = true)
    public AzureCosmosDBNoSqlContentRetriever(
            String endpoint,
            AzureKeyCredential keyCredential,
            TokenCredential tokenCredential,
            EmbeddingModel embeddingModel,
            String databaseName,
            String containerName,
            String partitionKeyPath,
            IndexingPolicy indexingPolicy,
            CosmosVectorEmbeddingPolicy cosmosVectorEmbeddingPolicy,
            CosmosFullTextPolicy cosmosFullTextPolicy,
            Integer vectorStoreThroughput,
            AzureCosmosDBSearchQueryType azureCosmosDBSearchQueryType,
            Integer maxResults,
            Double minScore,
            Filter filter) {
        ensureNotNull(endpoint, "endpoint");
        ensureTrue(
                (keyCredential != null && tokenCredential == null)
                        || (keyCredential == null && tokenCredential != null),
                "either keyCredential or tokenCredential must be set");

        if (keyCredential != null) {
            this.initialize(
                    endpoint,
                    keyCredential,
                    null,
                    databaseName,
                    containerName,
                    partitionKeyPath,
                    indexingPolicy,
                    cosmosVectorEmbeddingPolicy,
                    cosmosFullTextPolicy,
                    vectorStoreThroughput,
                    azureCosmosDBSearchQueryType,
                    null);
        } else {
            this.initialize(
                    endpoint,
                    null,
                    tokenCredential,
                    databaseName,
                    containerName,
                    partitionKeyPath,
                    indexingPolicy,
                    cosmosVectorEmbeddingPolicy,
                    cosmosFullTextPolicy,
                    vectorStoreThroughput,
                    azureCosmosDBSearchQueryType,
                    null);
        }

        this.embeddingModel = embeddingModel;
        this.azureCosmosDBSearchQueryType = azureCosmosDBSearchQueryType;
        this.maxResults = maxResults;
        this.minScore = minScore;
        this.filter = filter;
    }

    @Override
    public List<Content> retrieve(final Query query) {
        return switch (azureCosmosDBSearchQueryType) {
            case VECTOR -> retrieveWithVectorSearch(query);
            case FULL_TEXT_SEARCH -> retrieveWithFullTextSearch(query);
            case FULL_TEXT_RANKING -> retrieveWithFullTextRanking(query);
            case HYBRID -> retrieveWithHybridSearch(query);
            default ->
                throw new AzureCosmosDBNoSqlRuntimeException(
                        "Unknown Azure AI Search Query Type: " + azureCosmosDBSearchQueryType);
        };
    }

    private List<Content> retrieveWithVectorSearch(Query query) {
        Embedding referenceEmbedding = embeddingModel.embed(query.text()).content();
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(referenceEmbedding)
                .maxResults(maxResults)
                .minScore(minScore)
                .filter(filter)
                .build();

        List<EmbeddingMatch<TextSegment>> searchResult = super.search(request).matches();
        return mapToContentWithScore(searchResult);
    }

    private List<Content> retrieveWithFullTextSearch(Query query) {
        String content = query.text();
        List<EmbeddingMatch<TextSegment>> searchResult = super.findRelevantWithFullTextSearch(
                        content, this.maxResults, this.minScore, this.filter)
                .matches();
        return mapToContent(searchResult);
    }

    private List<Content> retrieveWithFullTextRanking(Query query) {
        String content = query.text();
        List<EmbeddingMatch<TextSegment>> searchResult = super.findRelevantWithFullTextRanking(
                        content, this.maxResults, this.minScore, this.filter)
                .matches();
        return mapToContent(searchResult);
    }

    private List<Content> retrieveWithHybridSearch(Query query) {
        Embedding referenceEmbedding = embeddingModel.embed(query.text()).content();
        String content = query.text();
        List<EmbeddingMatch<TextSegment>> searchResult = super.findRelevantWithHybridSearch(
                        referenceEmbedding, content, this.maxResults, this.minScore, this.filter)
                .matches();
        return mapToContentWithScore(searchResult);
    }

    private List<Content> mapToContent(List<EmbeddingMatch<TextSegment>> searchResult) {
        return searchResult.stream()
                .map(embeddingMatch -> Content.from(embeddingMatch.embedded()))
                .toList();
    }

    private List<Content> mapToContentWithScore(List<EmbeddingMatch<TextSegment>> searchResult) {
        return searchResult.stream()
                .map(embeddingMatch -> Content.from(
                        embeddingMatch.embedded(),
                        Map.of(
                                ContentMetadata.SCORE, embeddingMatch.score(),
                                ContentMetadata.EMBEDDING_ID, embeddingMatch.embedding())))
                .toList();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String endpoint;
        private AzureKeyCredential keyCredential;
        private TokenCredential tokenCredential;
        private EmbeddingModel embeddingModel;
        private String databaseName;
        private String containerName;
        private String partitionKeyPath;
        private IndexingPolicy indexingPolicy;
        private CosmosVectorEmbeddingPolicy cosmosVectorEmbeddingPolicy;
        private CosmosFullTextPolicy cosmosFullTextPolicy;
        private Integer vectorStoreThroughput;
        private AzureCosmosDBSearchQueryType azureCosmosDBSearchQueryType;
        private Integer maxResults;
        private Double minScore;
        private Filter filter;

        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.keyCredential = new AzureKeyCredential(apiKey);
            return this;
        }

        public Builder tokenCredential(TokenCredential tokenCredential) {
            this.tokenCredential = tokenCredential;
            return this;
        }

        public Builder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        public Builder containerName(String containerName) {
            this.containerName = containerName;
            return this;
        }

        public Builder partitionKeyPath(String partitionKeyPath) {
            this.partitionKeyPath = partitionKeyPath;
            return this;
        }

        public Builder indexingPolicy(IndexingPolicy indexingPolicy) {
            this.indexingPolicy = indexingPolicy;
            return this;
        }

        public Builder cosmosVectorEmbeddingPolicy(CosmosVectorEmbeddingPolicy cosmosVectorEmbeddingPolicy) {
            this.cosmosVectorEmbeddingPolicy = cosmosVectorEmbeddingPolicy;
            return this;
        }

        public Builder cosmosFullTextPolicy(CosmosFullTextPolicy cosmosFullTextPolicy) {
            this.cosmosFullTextPolicy = cosmosFullTextPolicy;
            return this;
        }

        public Builder vectorStoreThroughput(Integer vectorStoreThroughput) {
            this.vectorStoreThroughput = vectorStoreThroughput;
            return this;
        }

        public Builder searchQueryType(AzureCosmosDBSearchQueryType azureCosmosDBSearchQueryType) {
            this.azureCosmosDBSearchQueryType = azureCosmosDBSearchQueryType;
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

        public Builder filter(Filter filter) {
            this.filter = filter;
            return this;
        }

        public AzureCosmosDBNoSqlContentRetriever build() {
            return new AzureCosmosDBNoSqlContentRetriever(this);
        }
    }
}
