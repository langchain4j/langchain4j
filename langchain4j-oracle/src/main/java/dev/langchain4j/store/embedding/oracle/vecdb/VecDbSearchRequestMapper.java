package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbDistanceMetric;

final class VecDbSearchRequestMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private VecDbSearchRequestMapper() {}

    static VecDbSearchParameters map(EmbeddingSearchRequest request, VecDbDistanceMetric metric) {
        ensureNotNull(request, "request");
        ensureNotNull(metric, "metric");

        return new VecDbSearchParameters(
                toSearchQueryJson(request.queryEmbedding()),
                VecDbFilters.toJson(request.filter()),
                request.maxResults(),
                true,
                advancedOptionsToJson(metric));
    }

    private static String toSearchQueryJson(Embedding embedding) {
        ensureNotNull(embedding, "queryEmbedding");

        ObjectNode query = OBJECT_MAPPER.createObjectNode();
        ArrayNode vector = query.putArray("vector");
        for (float value : embedding.vector()) {
            vector.add(value);
        }
        return query.toString();
    }

    private static String advancedOptionsToJson(VecDbDistanceMetric metric) {
        ObjectNode options = OBJECT_MAPPER.createObjectNode();
        options.put("distance_metric", metric.name());
        return options.toString();
    }

    record VecDbSearchParameters(
            String queryJson, String filtersJson, int maxResults, boolean includeVectors, String advancedOptionsJson) {}
}
