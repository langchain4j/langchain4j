package dev.langchain4j.rag.content.retriever.azure.search;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.BasicAuthenticationCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.search.documents.indexes.models.SearchIndex;
import com.azure.search.documents.models.SearchResult;
import com.azure.search.documents.models.SemanticSearchResult;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AzureAiSearchContentRetrieverTest {

    @Test
    public void testConstructorMandatoryParameters() {
        String endpoint = "http://localhost";
        AzureKeyCredential keyCredential = new AzureKeyCredential("TEST");
        TokenCredential tokenCredential = new BasicAuthenticationCredential("TEST", "TEST");
        int dimensions = 1536;
        SearchIndex index = new SearchIndex("TEST");
        EmbeddingModel embeddingModel  = new AllMiniLmL6V2QuantizedEmbeddingModel();

        // Test empty endpoint
        try {
            new AzureAiSearchContentRetriever(null, keyCredential, tokenCredential, true, dimensions, index, null, embeddingModel, 3, 0, AzureAiSearchQueryType.VECTOR, null, null);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("endpoint cannot be null");
        }

        // Test no credentials
        try {
            new AzureAiSearchContentRetriever(endpoint, null, null, true, dimensions, index, null, embeddingModel, 3, 0, AzureAiSearchQueryType.VECTOR, null, null);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("either keyCredential or tokenCredential must be set");
        }

        // Test both credentials
        try {
            new AzureAiSearchContentRetriever(endpoint, keyCredential, tokenCredential, true, dimensions, index, null, embeddingModel, 3, 0, AzureAiSearchQueryType.VECTOR, null, null);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("either keyCredential or tokenCredential must be set");
        }

        // Test no dimensions and no index, for a vector search
        try {
            new AzureAiSearchContentRetriever(endpoint, null, tokenCredential, true, 0, null, null, embeddingModel, 3, 0, AzureAiSearchQueryType.VECTOR, null, null);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("dimensions must be set to a positive, non-zero integer between 2 and 3072");
        }

        // Test dimensions > 0, for a full text search
        try {
            new AzureAiSearchContentRetriever(endpoint, keyCredential, null, true, dimensions, null, null, embeddingModel, 3, 0, AzureAiSearchQueryType.FULL_TEXT, null, null);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("for full-text search, dimensions must be 0");
        }

        // Test no embedding model, for a vector search
        try {
            new AzureAiSearchContentRetriever(endpoint, keyCredential, null, true, 0, null, null, null, 3, 0, AzureAiSearchQueryType.VECTOR, null, null);
            fail("Expected IllegalArgumentException to be thrown");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).isEqualTo("embeddingModel cannot be null");
        }
    }

    @Test
    public void testFromAzureScoreToRelevanceScore_VECTOR() {
        SearchResult mockResult = mock(SearchResult.class);
        when(mockResult.getScore()).thenReturn(0.6);

        double result = AzureAiSearchContentRetriever.fromAzureScoreToRelevanceScore(mockResult, AzureAiSearchQueryType.VECTOR);

        assertThat(result).isEqualTo(0.6666666666666666);
    }

    @Test
    public void testFromAzureScoreToRelevanceScore_FULL_TEXT() {
        SearchResult mockResult = mock(SearchResult.class);
        when(mockResult.getScore()).thenReturn(0.4);

        double result = AzureAiSearchContentRetriever.fromAzureScoreToRelevanceScore(mockResult, AzureAiSearchQueryType.FULL_TEXT);

        assertThat(result).isEqualTo(0.4);
    }

    @Test
    public void testFromAzureScoreToRelevanceScore_HYBRID() {
        SearchResult mockResult = mock(SearchResult.class);
        when(mockResult.getScore()).thenReturn(0.7);

        double result = AzureAiSearchContentRetriever.fromAzureScoreToRelevanceScore(mockResult, AzureAiSearchQueryType.HYBRID);

        assertThat(result).isEqualTo(0.7);
    }

    @Test
    public void testFromAzureScoreToRelevanceScore_HYBRID_WITH_RERANKING() {
        SearchResult mockResult = mock(SearchResult.class);
        SemanticSearchResult mockSemanticSearchResult = mock(SemanticSearchResult.class);

        when(mockResult.getSemanticSearch()).thenReturn(mockSemanticSearchResult);
        when(mockSemanticSearchResult.getRerankerScore()).thenReturn(1.5);

        double result = AzureAiSearchContentRetriever.fromAzureScoreToRelevanceScore(mockResult, AzureAiSearchQueryType.HYBRID_WITH_RERANKING);

        assertThat(result).isEqualTo(0.375);
    }
}
