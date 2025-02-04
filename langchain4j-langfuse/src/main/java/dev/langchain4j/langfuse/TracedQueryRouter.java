package dev.langchain4j.langfuse;

import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.QueryRouter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracedQueryRouter implements QueryRouter {

    private static final Logger log = LoggerFactory.getLogger(TracedQueryRouter.class);
    private final QueryRouter delegate;
    private final LangfuseTracer tracer;

    public TracedQueryRouter(QueryRouter delegate, LangfuseTracer tracer) {
        this.delegate = delegate;
        this.tracer = tracer;
    }

    @Override
    public Collection<ContentRetriever> route(Query query) {
        var trace = tracer.startTrace("query-router", createTraceInput(query), null);

        var span = tracer.startSpan(trace, "query-routing", createSpanInput(query), null);

        try {
            Collection<ContentRetriever> result = delegate.route(query);

            Map<String, Object> output = createSuccessOutput(result);

            tracer.updateSpan(span, output, "SUCCESS");
            tracer.endSpan(span, output, "SUCCESS");
            tracer.endTrace(trace, output, "SUCCESS");

            log.debug("Query routing completed. Query: '{}', Routed to {} retrievers", query.text(), result.size());

            return result;

        } catch (Exception e) {
            Map<String, Object> errorOutput = createErrorOutput(e);

            tracer.updateSpan(span, errorOutput, "ERROR");
            tracer.endSpan(span, errorOutput, "ERROR");
            tracer.endTrace(trace, errorOutput, "ERROR");

            log.error("Query routing failed for query: '{}'", query.text(), e);
            throw e;
        }
    }

    private Map<String, Object> createTraceInput(Query query) {
        Map<String, Object> input = new HashMap<>();
        input.put("query_text", query.text());
        input.put("query_type", query.getClass().getSimpleName());

        return input;
    }

    private Map<String, Object> createSpanInput(Query query) {
        Map<String, Object> input = new HashMap<>();
        input.put("query_text", query.text());
        input.put("query_length", query.text().length());
        input.put("timestamp", System.currentTimeMillis());

        input.put("query_analysis", analyzeQuery(query));

        return input;
    }

    private Map<String, Object> createSuccessOutput(Collection<ContentRetriever> retrievers) {
        Map<String, Object> output = new HashMap<>();
        output.put("retriever_count", retrievers.size());

        List<Map<String, Object>> retrieverDetails =
                retrievers.stream().map(this::getRetrieverInfo).collect(Collectors.toList());
        output.put("retrievers", retrieverDetails);

        output.put("routing_time_ms", System.currentTimeMillis());

        return output;
    }

    private Map<String, Object> createErrorOutput(Exception e) {
        Map<String, Object> errorOutput = new HashMap<>();
        errorOutput.put("error_type", e.getClass().getName());
        errorOutput.put("error_message", e.getMessage());
        errorOutput.put("stack_trace", getStackTrace(e));
        errorOutput.put("timestamp", System.currentTimeMillis());

        if (e.getCause() != null) {
            errorOutput.put("cause", e.getCause().getMessage());
        }

        return errorOutput;
    }

    private Map<String, Object> analyzeQuery(Query query) {
        Map<String, Object> analysis = new HashMap<>();
        String queryText = query.text().trim();

        analysis.put("character_count", queryText.length());
        analysis.put("word_count", queryText.split("\\s+").length);

        analysis.put("contains_question_mark", queryText.contains("?"));
        analysis.put(
                "is_question",
                queryText.toLowerCase().startsWith("what")
                        || queryText.toLowerCase().startsWith("who")
                        || queryText.toLowerCase().startsWith("how")
                        || queryText.toLowerCase().startsWith("why"));

        return analysis;
    }

    private Map<String, Object> getRetrieverInfo(ContentRetriever retriever) {
        Map<String, Object> info = new HashMap<>();
        info.put("type", retriever.getClass().getSimpleName());
        info.put("hash_code", retriever.hashCode());
        return info;
    }

    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    public static class QueryRoutingException extends RuntimeException {
        public QueryRoutingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
