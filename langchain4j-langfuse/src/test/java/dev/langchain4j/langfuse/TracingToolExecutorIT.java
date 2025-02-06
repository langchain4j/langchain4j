package dev.langchain4j.langfuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.langfuse.core.LangfuseTracer;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.query.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TracedContentAggregatorIT {

    @Mock
    private ContentAggregator mockDelegate;

    @Mock
    private LangfuseTracer mockTracer;

    @Mock
    private Span mockSpan;

    @Mock
    private Trace mockTrace;

    private TracedContentAggregator tracedAggregator;

    private Map<Query, Collection<List<Content>>> sampleQueryToContents;

    @BeforeEach
    void setUp() {
        tracedAggregator = new TracedContentAggregator(mockDelegate, mockTracer);
        sampleQueryToContents = createSampleQueryToContents();

        when(mockTracer.startTrace(eq("content-aggregator"), anyMap())).thenReturn(mockTrace);
        when(mockTracer.startSpan(eq(mockTrace), eq("content-aggregation"), anyMap(), any()))
                .thenReturn(mockSpan);
    }

    @Test
    void shouldAggregateContentSuccessfully() {
        // Given
        List<Content> expectedResult = createSampleContent();
        when(mockDelegate.aggregate(sampleQueryToContents)).thenReturn(expectedResult);

        // When
        List<Content> result = tracedAggregator.aggregate(sampleQueryToContents);

        // Then
        assertThat(result).isEqualTo(expectedResult);

        // Verify tracing
        verify(mockTracer).startTrace(eq("content-aggregator"), anyMap());
        verify(mockTracer).startSpan(eq(mockTrace), eq("content-aggregation"), anyMap(), any());
        verify(mockTracer).updateSpan(eq(mockSpan), anyMap(), eq("SUCCESS"));
        verify(mockTracer).endSpan(eq(mockSpan), anyMap(), eq("SUCCESS"));
        verify(mockTracer).endTrace(eq(mockTrace), anyMap(), eq("SUCCESS"));
    }

    @Test
    void shouldHandleAggregationError() {
        // Given
        RuntimeException expectedException = new RuntimeException("Aggregation failed");
        when(mockDelegate.aggregate(sampleQueryToContents)).thenThrow(expectedException);

        // When/Then
        assertThatThrownBy(() -> tracedAggregator.aggregate(sampleQueryToContents))
                .isEqualTo(expectedException);

        // Verify error tracing
        verify(mockTracer).startTrace(eq("content-aggregator"), anyMap());
        verify(mockTracer).startSpan(eq(mockTrace), eq("content-aggregation"), anyMap(), any());
        verify(mockTracer).updateSpan(eq(mockSpan), anyMap(), eq("ERROR"));
        verify(mockTracer).endSpan(eq(mockSpan), anyMap(), eq("ERROR"));
        verify(mockTracer).endTrace(eq(mockTrace), anyMap(), eq("ERROR"));
    }

    @Test
    void shouldCalculateContentStatsCorrectly() {
        // Given
        List<Content> sampleContent = createSampleContent();
        when(mockDelegate.aggregate(sampleQueryToContents)).thenReturn(sampleContent);

        // When
        tracedAggregator.aggregate(sampleQueryToContents);

        // Then
        verify(mockTracer)
                .updateSpan(
                        eq(mockSpan),
                        argThat(output -> {
                            Map<String, Object> stats = (Map<String, Object>) output.get("content_stats");
                            return stats != null
                                    && stats.containsKey("total_content")
                                    && stats.containsKey("average_content_length")
                                    && stats.containsKey("metadata_keys_frequency");
                        }),
                        eq("SUCCESS"));
    }

    @Test
    void shouldHandleEmptyQueryMap() {
        // Given
        Map<Query, Collection<List<Content>>> emptyMap = new HashMap<>();
        List<Content> emptyResult = new ArrayList<>();
        when(mockDelegate.aggregate(emptyMap)).thenReturn(emptyResult);

        // When
        List<Content> result = tracedAggregator.aggregate(emptyMap);

        // Then
        assertThat(result).isEmpty();
        verify(mockTracer)
                .startTrace(eq("content-aggregator"), argThat(input -> ((Integer) input.get("total_queries")) == 0));
    }

    // Helper methods
    private Map<Query, Collection<List<Content>>> createSampleQueryToContents() {
        Map<Query, Collection<List<Content>>> map = new HashMap<>();
        Query query1 = Query.from("test query 1");
        Query query2 = Query.from("test query 2");

        Collection<List<Content>> contents1 = List.of(
                List.of(createContent("content 1", Map.of("source", "doc1"))),
                List.of(createContent("content 2", Map.of("source", "doc2"))));

        Collection<List<Content>> contents2 = List.of(List.of(createContent("content 3", Map.of("source", "doc3"))));

        map.put(query1, contents1);
        map.put(query2, contents2);
        return map;
    }

    private List<Content> createSampleContent() {
        return List.of(
                createContent("Sample content 1", Map.of("source", "doc1", "page", "1")),
                createContent("Sample content 2", Map.of("source", "doc2", "page", "2")),
                createContent("Sample content 3", Map.of("source", "doc3", "page", "3")));
    }

    private Content createContent(String text, Map<String, String> metadata) {
        return Content.from(text, metadata);
    }

    // Custom argument matcher for verifying map contents
    private static class MapContentMatcher implements ArgumentMatcher<Map<String, Object>> {
        private final String expectedKey;
        private final Object expectedValue;

        MapContentMatcher(String expectedKey, Object expectedValue) {
            this.expectedKey = expectedKey;
            this.expectedValue = expectedValue;
        }

        @Override
        public boolean matches(Map<String, Object> argument) {
            return argument != null
                    && argument.containsKey(expectedKey)
                    && expectedValue.equals(argument.get(expectedKey));
        }
    }
}
