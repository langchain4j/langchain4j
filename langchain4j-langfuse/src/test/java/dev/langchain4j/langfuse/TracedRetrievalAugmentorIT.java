package dev.langchain4j.langfuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TracedRetrievalAugmentorIT {

    private RetrievalAugmentor delegate;
    private LangfuseTracer tracer;
    private QueryTransformer queryTransformer;
    private QueryRouter queryRouter;
    private ContentAggregator contentAggregator;
    private ContentInjector contentInjector;

    @BeforeEach
    void setUp() {
        delegate = mock(RetrievalAugmentor.class);
        tracer = mock(LangfuseTracer.class);
        queryTransformer = mock(QueryTransformer.class);
        queryRouter = mock(QueryRouter.class);
        contentAggregator = mock(ContentAggregator.class);
        contentInjector = mock(ContentInjector.class);
    }

    @Test
    void testAugment() {
        // Given
        ChatMessage chatMessage = new ChatMessage("user", "Hello, world!");
        AugmentationRequest request = new AugmentationRequest(chatMessage, Map.of());
        Query query = Query.from("Hello, world!", Map.of());
        Collection<Query> queries = List.of(query);
        ContentRetriever retriever = mock(ContentRetriever.class);
        when(queryRouter.route(query)).thenReturn(List.of(retriever));
        when(retriever.retrieve(query)).thenReturn(List.of(new dev.langchain4j.rag.content.Content("content1")));
        when(queryTransformer.transform(query)).thenReturn(queries);
        when(contentAggregator.aggregate(anyMap()))
                .thenReturn(List.of(new dev.langchain4j.rag.content.Content("aggregated")));
        when(contentInjector.inject(anyList(), eq(chatMessage))).thenReturn(chatMessage);
        when(delegate.augment(request)).thenReturn(AugmentationResult.builder().build());

        TracedRetrievalAugmentor augmentor = new TracedRetrievalAugmentor(
                delegate, tracer, queryTransformer, queryRouter, contentAggregator, contentInjector);

        // When
        AugmentationResult result = augmentor.augment(request);

        // Then
        verify(tracer).startTrace("rag-augmentation", anyMap());
        verify(tracer).startSpan(any(), anyString(), anyMap(), anyString());
        verify(tracer, times(6)).endSpan(any(), anyMap(), eq("SUCCESS"));
        verify(tracer).endTrace(any(), anyMap(), eq("SUCCESS"));
        assertThat(result).isNotNull();
    }
}
