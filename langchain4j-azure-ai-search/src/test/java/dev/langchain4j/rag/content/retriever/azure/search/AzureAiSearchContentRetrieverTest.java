package dev.langchain4j.rag.content.retriever.azure.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.BasicAuthenticationCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.search.documents.indexes.models.SearchIndex;
import com.azure.search.documents.models.SearchResult;
import com.azure.search.documents.models.SemanticSearchResult;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class AzureAiSearchContentRetrieverTest {

    @Test
    void constructorMandatoryParameters() {
        String endpoint = "http://localhost";
        AzureKeyCredential keyCredential = new AzureKeyCredential("TEST");
        TokenCredential tokenCredential = new BasicAuthenticationCredential("TEST", "TEST");
        int dimensions = 1536;
        SearchIndex index = new SearchIndex("TEST");
        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        // Test empty endpoint
        try {
            new AzureAiSearchContentRetriever(
                    null,
                    keyCredential,
                    tokenCredential,
                    true,
                    dimensions,
                    index,
                    null,
                    embeddingModel,
                    3,
                    0,
                    AzureAiSearchQueryType.VECTOR,
                    null,
                    null);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("endpoint cannot be null");
        }

        // Test no credentials
        try {
            new AzureAiSearchContentRetriever(
                    endpoint,
                    null,
                    null,
                    true,
                    dimensions,
                    index,
                    null,
                    embeddingModel,
                    3,
                    0,
                    AzureAiSearchQueryType.VECTOR,
                    null,
                    null);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("either keyCredential or tokenCredential must be set");
        }

        // Test both credentials
        try {
            new AzureAiSearchContentRetriever(
                    endpoint,
                    keyCredential,
                    tokenCredential,
                    true,
                    dimensions,
                    index,
                    null,
                    embeddingModel,
                    3,
                    0,
                    AzureAiSearchQueryType.VECTOR,
                    null,
                    null);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("either keyCredential or tokenCredential must be set");
        }

        // Test no dimensions and no index, for a vector search
        try {
            new AzureAiSearchContentRetriever(
                    endpoint,
                    null,
                    tokenCredential,
                    true,
                    0,
                    null,
                    null,
                    embeddingModel,
                    3,
                    0,
                    AzureAiSearchQueryType.VECTOR,
                    null,
                    null);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage())
                    .isEqualTo("dimensions must be set to a positive, non-zero integer between 2 and 3072");
        }

        // Test dimensions > 0, for a full text search
        try {
            new AzureAiSearchContentRetriever(
                    endpoint,
                    keyCredential,
                    null,
                    true,
                    dimensions,
                    null,
                    null,
                    embeddingModel,
                    3,
                    0,
                    AzureAiSearchQueryType.FULL_TEXT,
                    null,
                    null);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("for full-text search, dimensions must be 0");
        }

        // Test no embedding model, for a vector search
        try {
            new AzureAiSearchContentRetriever(
                    endpoint,
                    keyCredential,
                    null,
                    true,
                    0,
                    null,
                    null,
                    null,
                    3,
                    0,
                    AzureAiSearchQueryType.VECTOR,
                    null,
                    null);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("embeddingModel cannot be null");
        }
    }

    @Test
    void fromAzureScoreToRelevanceScoreVECTOR() {
        SearchResult mockResult = mock(SearchResult.class);
        when(mockResult.getScore()).thenReturn(0.6);

        double result =
                AzureAiSearchContentRetriever.fromAzureScoreToRelevanceScore(mockResult, AzureAiSearchQueryType.VECTOR);

        assertThat(result).isEqualTo(0.6666666666666666);
    }

    @Test
    void fromAzureScoreToRelevanceScoreFULLTEXT() {
        SearchResult mockResult = mock(SearchResult.class);
        when(mockResult.getScore()).thenReturn(0.4);

        double result = AzureAiSearchContentRetriever.fromAzureScoreToRelevanceScore(
                mockResult, AzureAiSearchQueryType.FULL_TEXT);

        assertThat(result).isEqualTo(0.4);
    }

    @Test
    void fromAzureScoreToRelevanceScoreHYBRID() {
        SearchResult mockResult = mock(SearchResult.class);
        when(mockResult.getScore()).thenReturn(0.7);

        double result =
                AzureAiSearchContentRetriever.fromAzureScoreToRelevanceScore(mockResult, AzureAiSearchQueryType.HYBRID);

        assertThat(result).isEqualTo(0.7);
    }

    @Test
    void fromAzureScoreToRelevanceScoreHYBRIDWITHRERANKING() {
        SearchResult mockResult = mock(SearchResult.class);
        SemanticSearchResult mockSemanticSearchResult = mock(SemanticSearchResult.class);

        when(mockResult.getSemanticSearch()).thenReturn(mockSemanticSearchResult);
        when(mockSemanticSearchResult.getRerankerScore()).thenReturn(1.5);

        double result = AzureAiSearchContentRetriever.fromAzureScoreToRelevanceScore(
                mockResult, AzureAiSearchQueryType.HYBRID_WITH_RERANKING);

        assertThat(result).isEqualTo(0.375);
    }

    @Test
    void retrieveVector() {
        TextSegment textSegment = mock(TextSegment.class);
        EmbeddingMatch embeddingMatch = mock(EmbeddingMatch.class);
        when(embeddingMatch.score()).thenReturn(0.2);
        when(embeddingMatch.embeddingId()).thenReturn("embedding-123");
        when(embeddingMatch.embedded()).thenReturn(textSegment);

        EmbeddingSearchResult mockEmbeddingSearchResult = mock(EmbeddingSearchResult.class);
        when(mockEmbeddingSearchResult.matches()).thenReturn(List.of(embeddingMatch));

        EmbeddingModel mockEmbeddingModel = mock(EmbeddingModel.class);
        when(mockEmbeddingModel.embed(anyString())).thenReturn(Response.from(mock(Embedding.class)));

        AzureAiSearchContentRetriever retriever = spy(new AzureAiSearchContentRetriever(
                "http://localhost",
                new AzureKeyCredential("TEST"),
                null,
                false,
                10,
                null,
                "test-index",
                mockEmbeddingModel,
                3,
                0.5,
                AzureAiSearchQueryType.VECTOR,
                null,
                null));

        doReturn(mockEmbeddingSearchResult).when(retriever).search(any(EmbeddingSearchRequest.class));

        List<Content> results = retriever.retrieve(Query.from("test"));
        assertThat(results).hasSize(1);
        assertThat(results.get(0).metadata().get(ContentMetadata.SCORE)).isInstanceOf(Double.class);
        assertThat(results.get(0).metadata()).containsEntry(ContentMetadata.SCORE, 0.2);
        assertThat(results.get(0).metadata().get(ContentMetadata.EMBEDDING_ID)).isInstanceOf(String.class);
        assertThat(results.get(0).metadata()).containsEntry(ContentMetadata.EMBEDDING_ID, "embedding-123");
        assertThat(results.get(0).textSegment()).isEqualTo(textSegment);
    }
}
