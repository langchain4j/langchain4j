package dev.langchain4j.langfuse;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.query.Query;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracedContentAggregator implements ContentAggregator {

    private static final Logger log = LoggerFactory.getLogger(TracedContentAggregator.class);
    private final ContentAggregator delegate;
    private final LangfuseTracer tracer;

    public TracedContentAggregator(ContentAggregator delegate, LangfuseTracer tracer) {
        this.delegate = delegate;
        this.tracer = tracer;
    }

    @Override
    public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {
        var traceInput = createTraceInput(queryToContents);
        log.debug("Starting content aggregation trace with input: {}", traceInput);
        var trace = tracer.startTrace("content-aggregator", traceInput, null);

        var spanInput = Map.of(
                "query_count", queryToContents.size(),
                "content_groups", getContentGroupsStats(queryToContents));
        log.debug("Starting content aggregation span with input: {}", spanInput);
        var span = tracer.startSpan(trace, "content-aggregation", spanInput, null);

        try {
            List<Content> result = delegate.aggregate(queryToContents);

            Map<String, Object> output = new HashMap<>();
            output.put("aggregated_content_count", result.size());
            output.put("content_stats", getContentStats(result));

            log.debug("Content aggregation successful, updating span with output: {}", output);
            tracer.updateSpan(span, output, "SUCCESS");

            log.debug("Ending content aggregation span with output: {}", output);
            tracer.endSpan(span, output, "SUCCESS");

            log.debug("Ending content aggregation trace with output: {}", output);
            tracer.endTrace(trace, output, "SUCCESS");

            return result;

        } catch (Exception e) {
            Map<String, Object> errorOutput = new HashMap<>();
            errorOutput.put("error_type", e.getClass().getName());
            errorOutput.put("error_message", e.getMessage());
            errorOutput.put("stack_trace", getStackTrace(e));

            log.error("Content aggregation failed, updating span with error output: {}", errorOutput, e);
            tracer.updateSpan(span, errorOutput, "ERROR");

            log.debug("Ending content aggregation span with error output: {}", errorOutput);
            tracer.endSpan(span, errorOutput, "ERROR");

            log.debug("Ending content aggregation trace with error output: {}", errorOutput);
            tracer.endTrace(trace, errorOutput, "ERROR");

            log.error("Content aggregation failed", e);
            throw e;
        }
    }

    private Map<String, Object> createTraceInput(Map<Query, Collection<List<Content>>> queryToContents) {
        return Map.of(
                "total_queries",
                queryToContents.size(),
                "query_details",
                queryToContents.keySet().stream().map(Query::text).collect(Collectors.toList()));
    }

    private Map<String, Object> getContentGroupsStats(Map<Query, Collection<List<Content>>> queryToContents) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("total_queries", queryToContents.size());
        stats.put(
                "total_content_groups",
                queryToContents.values().stream().mapToInt(Collection::size).sum());

        stats.put(
                "content_per_query",
                queryToContents.entrySet().stream()
                        .collect(Collectors.toMap(entry -> entry.getKey().text(), entry -> entry.getValue()
                                .size())));

        return stats;
    }

    private Map<String, Object> getContentStats(List<Content> contents) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("total_content", contents.size());

        // record content length statistics
        OptionalDouble avgLength = contents.stream()
                .mapToInt(content -> content.textSegment().text().length())
                .average();

        stats.put("average_content_length", avgLength.orElse(0.0));

        // collect metedata statistics
        // Map<String, Integer> metadataKeys = contents.stream()
        //         .flatMap(content -> content.metadata().keySet().stream())
        //         .collect(Collectors.groupingBy(
        //                 key -> key, Collectors.collectingAndThen(Collectors.counting(),
        // Long::intValue)));

        // stats.put("metadata_keys_frequency", metadataKeys);

        return stats;
    }

    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}
