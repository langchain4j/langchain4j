package dev.langchain4j.rag.content.retriever.azure.search;

import com.azure.search.documents.models.SearchResult;
import com.azure.search.documents.models.SemanticSearchResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AzureAiSearchContentRetrieverTest {

    @Test
    public void testFromAzureScoreToRelevanceScore_VECTOR() {
        SearchResult mockResult = mock(SearchResult.class);
        when(mockResult.getScore()).thenReturn(0.6);

        double result = AzureAiSearchContentRetriever.fromAzureScoreToRelevanceScore(mockResult, AzureAiSearchQueryType.VECTOR);

        assertEquals(0.6666666666666666, result);
    }

    @Test
    public void testFromAzureScoreToRelevanceScore_FULL_TEXT() {
        SearchResult mockResult = mock(SearchResult.class);
        when(mockResult.getScore()).thenReturn(0.4);

        double result = AzureAiSearchContentRetriever.fromAzureScoreToRelevanceScore(mockResult, AzureAiSearchQueryType.FULL_TEXT);

        assertEquals(0.4, result);
    }

    @Test
    public void testFromAzureScoreToRelevanceScore_HYBRID() {
        SearchResult mockResult = mock(SearchResult.class);
        when(mockResult.getScore()).thenReturn(0.7);

        double result = AzureAiSearchContentRetriever.fromAzureScoreToRelevanceScore(mockResult, AzureAiSearchQueryType.HYBRID);

        assertEquals(0.7, result);
    }

    @Test
    public void testFromAzureScoreToRelevanceScore_HYBRID_WITH_RERANKING() {
        SearchResult mockResult = mock(SearchResult.class);
        SemanticSearchResult mockSemanticSearchResult = mock(SemanticSearchResult.class);

        when(mockResult.getSemanticSearch()).thenReturn(mockSemanticSearchResult);
        when(mockSemanticSearchResult.getRerankerScore()).thenReturn(1.5);

        double result = AzureAiSearchContentRetriever.fromAzureScoreToRelevanceScore(mockResult, AzureAiSearchQueryType.HYBRID_WITH_RERANKING);

        assertEquals(0.375, result);
    }
}
