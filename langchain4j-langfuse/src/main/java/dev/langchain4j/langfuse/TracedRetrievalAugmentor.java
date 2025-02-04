package dev.langchain4j.langfuse;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.rag.AugmentationRequest;
import dev.langchain4j.rag.AugmentationResult;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracedRetrievalAugmentor implements RetrievalAugmentor {
    private static final Logger log = LoggerFactory.getLogger(TracedRetrievalAugmentor.class);

    private final RetrievalAugmentor delegate;
    private final LangfuseTracer tracer;

    private final QueryTransformer queryTransformer;
    private final QueryRouter queryRouter;
    private final ContentAggregator contentAggregator;
    private final ContentInjector contentInjector;

    public TracedRetrievalAugmentor(
            RetrievalAugmentor delegate,
            LangfuseTracer tracer,
            QueryTransformer queryTransformer,
            QueryRouter queryRouter,
            ContentAggregator contentAggregator,
            ContentInjector contentInjector) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.queryTransformer = queryTransformer;
        this.queryRouter = queryRouter;
        this.contentAggregator = contentAggregator;
        this.contentInjector = contentInjector;
    }

    @Override
    public AugmentationResult augment(AugmentationRequest augmentationRequest) {
        var trace = tracer.startTrace("rag-augmentation", Map.of("augmentationRequest", augmentationRequest), null);
        var span =
                tracer.startSpan(trace, "rag-augmentation", Map.of("augmentationRequest", augmentationRequest), null);
        try {
            Query originalQuery = Query.from(augmentationRequest.chatMessage().text(), augmentationRequest.metadata());

            var transformedQuerySpan =
                    tracer.startSpan(trace, "query-transformation", Map.of("originalQuery", originalQuery), span, null);
            QueryTransformer tracedQueryTransformer = new TracedQueryTransformer(queryTransformer, tracer, trace);
            var queries = tracedQueryTransformer.transform(originalQuery);
            tracer.endSpan(transformedQuerySpan, Map.of("queries", queries), "SUCCESS");

            Map<Query, Collection<List<Content>>> queryToContents = process(trace, queries, span);

            var aggregatorSpan =
                    tracer.startSpan(trace, "content-aggregation", Map.of("queryToContents", queryToContents), span);
            ContentAggregator tracedContentAggregator = new TracedContentAggregator(contentAggregator, tracer);
            var contents = tracedContentAggregator.aggregate(queryToContents);
            tracer.endSpan(aggregatorSpan, Map.of("contents", contents), "SUCCESS");

            var contentInjectorSpan = tracer.startSpan(
                    trace,
                    "content-injection",
                    Map.of("contents", contents, "chatMessage", augmentationRequest.chatMessage()),
                    span);
            ContentInjector tracedContentInjector = new TracedContentInjector(contentInjector, tracer);
            var augmentedChatMessage = tracedContentInjector.inject(contents, augmentationRequest.chatMessage());
            tracer.endSpan(contentInjectorSpan, Map.of("augmentedChatMessage", augmentedChatMessage), "SUCCESS");

            AugmentationResult result = AugmentationResult.builder()
                    .chatMessage(augmentedChatMessage)
                    .contents(contents)
                    .build();
            tracer.updateSpan(span, Map.of("result", result), "SUCCESS");
            tracer.endSpan(span, Map.of("result", result), "SUCCESS");
            tracer.endTrace(trace, Map.of("result", result), "SUCCESS");
            return result;

        } catch (Throwable e) {
            tracer.endSpan(span, Map.of("error", e), "ERROR");
            tracer.endTrace(trace, Map.of("error", e), "ERROR");
            log.error("", e);
            throw e;
        }
    }

    @Override
    public UserMessage augment(UserMessage userMessage, Metadata metadata) {
        return delegate.augment(userMessage, metadata);
    }

    private Map<Query, Collection<List<dev.langchain4j.rag.content.Content>>> process(
            String traceId, Collection<Query> queries, String parentSpanId) {
        if (queries.size() == 1) {
            Query query = queries.iterator().next();
            var routeSpan = tracer.startSpan(traceId, "query-routing", Map.of("query", query), parentSpanId, null);
            QueryRouter tracedQueryRouter = new TracedQueryRouter(queryRouter, tracer);
            var retrievers = tracedQueryRouter.route(query);
            tracer.endSpan(routeSpan, Map.of("retrievers", retrievers), "SUCCESS");

            if (retrievers.size() == 1) {
                ContentRetriever contentRetriever = retrievers.iterator().next();
                var retrieverSpan = tracer.startSpan(
                        traceId,
                        "content-retrieval",
                        Map.of("query", query, "retriever", contentRetriever),
                        parentSpanId,
                        null);
                ContentRetriever tracedContentRetriever = new TracedContentRetriever(contentRetriever, tracer, traceId);
                var contents = tracedContentRetriever.retrieve(query);
                tracer.endSpan(retrieverSpan, Map.of("contents", contents), "SUCCESS");
                return Map.of();
            } else if (retrievers.size() > 1) {
                var retrieverSpan = tracer.startSpan(
                        traceId,
                        "content-retrieval-all",
                        Map.of("query", query, "retrievers", retrievers),
                        parentSpanId);
                List<List<Content>> contents = retrievers.stream()
                        .map(retriever -> new TracedContentRetriever(retriever, tracer, traceId).retrieve(query))
                        .collect(Collectors.toList());
                tracer.endSpan(retrieverSpan, Map.of("contents", contents), "SUCCESS");

                return Map.of(query, (Collection<List<Content>>) contents);
            } else {
                return Map.of();
            }
        } else if (queries.size() > 1) {
            return queries.stream().collect(Collectors.toMap(query -> query, query -> {
                var routeSpan = tracer.startSpan(traceId, "query-routing", Map.of("query", query), parentSpanId, null);
                QueryRouter tracedQueryRouter = new TracedQueryRouter(queryRouter, tracer);
                var retrievers = tracedQueryRouter.route(query);
                tracer.endSpan(routeSpan, Map.of("retrievers", retrievers), "SUCCESS");

                return retrievers.stream()
                        .map(retriever -> {
                            var retrieverSpan = tracer.startSpan(
                                    traceId,
                                    "content-retrieval",
                                    Map.of("query", query, "retriever", retriever),
                                    parentSpanId);
                            ContentRetriever tracedContentRetriever =
                                    new TracedContentRetriever(retriever, tracer, traceId);
                            var contents = tracedContentRetriever.retrieve(query);
                            tracer.endSpan(retrieverSpan, Map.of("contents", contents), "SUCCESS");
                            return contents;
                        })
                        .collect(Collectors.toList());
            }));

        } else {
            return Map.of();
        }
    }
}
