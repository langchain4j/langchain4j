package dev.langchain4j.langfuse;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracedContentRetriever implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(TracedContentRetriever.class);

    private final ContentRetriever delegate;
    private final LangfuseTracer tracer;
    private final String traceId;

    public TracedContentRetriever(ContentRetriever delegate, LangfuseTracer tracer, String traceId) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.traceId = traceId;
    }

    @Override
    public List<Content> retrieve(Query query) {
        // Start a span for the content retrieval process
        String spanId = tracer.startSpan(traceId, "content_retrieval", Map.of("query", query.toString()), null);

        try {
            // Delegate the retrieval to the actual implementation
            List<Content> contents = delegate.retrieve(query);

            // Prepare output data for tracing
            Map<String, Object> output = new HashMap<>();
            output.put("content_count", contents.size());
            output.put("contents", formatContents(contents));

            // End the span successfully
            tracer.endSpan(spanId, output, "Success");

            // Log a retrieval complete event
            tracer.logEvent(
                    traceId,
                    "retrieval_complete",
                    Map.of("query", query.toString(), "retrieved_count", contents.size()));

            return contents;

        } catch (Exception e) {
            // Handle any errors during retrieval
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("error_type", e.getClass().getName());
            errorData.put("error_message", e.getMessage());
            tracer.endSpan(spanId, errorData, "Error");

            log.error("Content retrieval failed", e);
            throw e;
        }
    }

    private List<Map<String, Object>> formatContents(List<Content> contents) {
        return contents.stream()
                .map(content -> {
                    Map<String, Object> contentData = new HashMap<>();
                    contentData.put("text", content.textSegment());
                    contentData.put("metadata", content.metadata());
                    return contentData;
                })
                .collect(Collectors.toList());
    }
}
