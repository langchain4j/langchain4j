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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AzureCosmosDBNoSqlContentRetriever extends AbstractAzureCosmosDBNoSqlEmbeddingStore
        implements ContentRetriever {

    private static final Logger logger = LoggerFactory.getLogger(AzureCosmosDBNoSqlContentRetriever.class);

    private final EmbeddingModel embeddingModel;
    private final AzureCosmosDBSearchQueryType azureCosmosDBSearchQueryType;
    private final int maxResults;
    private final double minScore;
    private final Filter filter;

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
}
