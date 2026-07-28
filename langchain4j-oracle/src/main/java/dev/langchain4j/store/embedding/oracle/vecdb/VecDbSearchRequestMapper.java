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

    static VecDbSearchParameters map(EmbeddingSearchRequest request, VecDbDistanceMetric distanceMetric) {
        ensureNotNull(request, "request");
        ensureNotNull(distanceMetric, "distanceMetric");

        return new VecDbSearchParameters(
                toSearchQueryJson(request.queryEmbedding()),
                VecDbFilters.toJson(request.filter()),
                request.maxResults(),
                true,
                toAdvancedOptionsJson(distanceMetric));
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

    private static String toAdvancedOptionsJson(VecDbDistanceMetric distanceMetric) {
        ObjectNode advancedOptions = OBJECT_MAPPER.createObjectNode();
        advancedOptions.put("distance_metric", distanceMetric.name());
        return advancedOptions.toString();
    }

    record VecDbSearchParameters(
            String queryJson, String filtersJson, int maxResults, boolean includeVectors, String advancedOptionsJson) {}
}
