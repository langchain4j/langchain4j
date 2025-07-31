package dev.langchain4j.rag.content.retriever.azure.cosmos.nosql;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import java.util.List;
import java.util.Map;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.TokenCredential;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AzureCosmosDBNoSqlContentRetriever extends AbstractAzureCosmosDBNoSqlEmbeddingStore implements ContentRetriever {

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
            Integer vectorStoreThroughput,
            String vectorIndexType,
            String vectorIndexPath,
            String vectorDataType,
            Integer vectorDimensions,
            String vectorDistanceFunction,
            AzureCosmosDBSearchQueryType azureCosmosDBSearchQueryType,
            Integer maxResults,
            Double minScore,
            Integer vectorQuantizationSizeInBytes,
            Integer vectorIndexingSearchListSize,
            List<String> vectorIndexShardKeys,
            String fullTextIndexPath,
            String fullTextIndexLanguage,
            Filter filter) {
        ensureNotNull(endpoint, "endpoint");
        ensureTrue(
                (keyCredential != null && tokenCredential == null)
                        || (keyCredential == null && tokenCredential != null),
                "either keyCredential or tokenCredential must be set");

        if (azureCosmosDBSearchQueryType.equals(AzureCosmosDBSearchQueryType.FULL_TEXT_SEARCH) || azureCosmosDBSearchQueryType.equals(AzureCosmosDBSearchQueryType.FULL_TEXT_RANKING)) {
            // Full-text search doesn't use embeddings, so dimensions must be 0
            ensureTrue(vectorDimensions == 0, "for full-text search, dimensions must be 0");
            ensureNotNull(fullTextIndexPath, "fullTextIndexPath");
            ensureNotNull(fullTextIndexLanguage, "fullTextIndexLanguage");
        } else {
            ensureNotNull(embeddingModel, "embeddingModel");
            ensureTrue(
                    vectorDimensions >= 2 && vectorDimensions <= 3072,
                    "dimensions must be set to a positive, non-zero integer between 2 and 3072");
        }

        if (keyCredential != null) {
            this.initialize(endpoint, keyCredential, null, databaseName, containerName, partitionKeyPath, vectorStoreThroughput, azureCosmosDBSearchQueryType, vectorIndexType, vectorIndexPath, vectorDataType, vectorDimensions, vectorDistanceFunction, vectorQuantizationSizeInBytes, vectorIndexingSearchListSize, vectorIndexShardKeys, fullTextIndexPath, fullTextIndexLanguage, null);
        } else {
            this.initialize(endpoint, null, tokenCredential, databaseName, containerName, partitionKeyPath, vectorStoreThroughput, azureCosmosDBSearchQueryType, vectorIndexType, vectorIndexPath, vectorDataType, vectorDimensions, vectorDistanceFunction, vectorQuantizationSizeInBytes, vectorIndexingSearchListSize, vectorIndexShardKeys, fullTextIndexPath, fullTextIndexLanguage, null);
        }

        this.embeddingModel = embeddingModel;
        this.azureCosmosDBSearchQueryType = azureCosmosDBSearchQueryType;
        this.maxResults = maxResults;
        this.minScore = minScore;
        this.filter = filter;
    }

    @Override
    public List<Content> retrieve(final Query query) {
        if (azureCosmosDBSearchQueryType.equals(AzureCosmosDBSearchQueryType.VECTOR)) {
            Embedding referenceEmbedding = embeddingModel.embed(query.text()).content();
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(referenceEmbedding)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .filter(filter)
                    .build();

            List<EmbeddingMatch<TextSegment>> searchResult =
                    super.search(request).matches();
            return searchResult.stream().map(embeddingMatch -> Content.from(
                            embeddingMatch.embedded(),
                            Map.of(
                                    ContentMetadata.SCORE, embeddingMatch.score(),
                                    ContentMetadata.EMBEDDING_ID, embeddingMatch.embedding())))
                    .toList();
        } else if (azureCosmosDBSearchQueryType.equals(AzureCosmosDBSearchQueryType.FULL_TEXT_SEARCH)) {
            String content = query.text();
            List<EmbeddingMatch<TextSegment>> searchResult = super.findRelevantWithFullTextSearch(content, this.maxResults, this.minScore, this.filter).matches();
            return searchResult.stream().map(embeddingMatch -> Content.from(
                            embeddingMatch.embedded()))
                    .toList();
        } else if (azureCosmosDBSearchQueryType.equals(AzureCosmosDBSearchQueryType.FULL_TEXT_RANKING)) {
            String content = query.text();
            List<EmbeddingMatch<TextSegment>> searchResult = super.findRelevantWithFullTextRanking(content, this.maxResults, this.minScore, this.filter).matches();
            return searchResult.stream().map(embeddingMatch -> Content.from(
                            embeddingMatch.embedded()))
                    .toList();
        } else if (azureCosmosDBSearchQueryType.equals(AzureCosmosDBSearchQueryType.HYBRID)) {
            Embedding referenceEmbedding = embeddingModel.embed(query.text()).content();
            String content = query.text();
            List<EmbeddingMatch<TextSegment>> searchResult = super.findRelevantWithHybridSearch(referenceEmbedding, content, this.maxResults, this.minScore, this.filter).matches();
            return searchResult.stream().map(embeddingMatch -> Content.from(
                            embeddingMatch.embedded(),
                            Map.of(
                                    ContentMetadata.SCORE, embeddingMatch.score(),
                                    ContentMetadata.EMBEDDING_ID, embeddingMatch.embedding())))
                    .toList();
        } else {
            throw new AzureCosmosDBNoSqlRuntimeException("Unknown Azure AI Search Query Type: " + azureCosmosDBSearchQueryType);
        }
    }
}
