package dev.langchain4j.langfuse;

import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracedQueryTransformer implements QueryTransformer {
    private static final Logger log = LoggerFactory.getLogger(TracedQueryTransformer.class);

    private final QueryTransformer delegate;
    private final LangfuseTracer tracer;
    private final String traceId;

    public TracedQueryTransformer(QueryTransformer delegate, LangfuseTracer tracer, String traceId) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.traceId = traceId;
    }

    @Override
    public Collection<Query> transform(Query query) {
        String spanId = tracer.startSpan(traceId, "query_transformation", Map.of("original_query", query), null);

        try {
            Collection<Query> transformedQuery = delegate.transform(query);

            Map<String, Object> output = new HashMap<>();
            output.put("original_query", query);
            output.put("transformed_query", transformedQuery);

            tracer.endSpan(spanId, output, null);

            return transformedQuery;

        } catch (Exception e) {
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("error_type", e.getClass().getName());
            errorData.put("error_message", e.getMessage());
            tracer.endSpan(spanId, errorData, null);

            log.error("Query transformation failed", e);
            throw e;
        }
    }
}
