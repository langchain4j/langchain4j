package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Maps {@link VecDbIndex} configuration to the {@code index_params} JSON accepted by
 * {@code DBMS_VECTOR_DATABASE}.
 */
final class VecDbIndexJsonMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private VecDbIndexJsonMapper() {}

    /**
     * Returns the VecDB {@code index_params} JSON for an index configuration.
     */
    static String toJson(VecDbIndex index) {
        ensureNotNull(index, "index");

        ObjectNode indexParameters = OBJECT_MAPPER.createObjectNode();
        indexParameters.put("indexing", "auto");
        indexParameters.put("organization", toDatabaseValue(index.organization()));
        indexParameters.put("distance_metric", index.distanceMetric().name());

        if (index.accuracy() != null) {
            indexParameters.put("accuracy", index.accuracy());
        }

        ObjectNode advancedParameters = OBJECT_MAPPER.createObjectNode();
        if (index.partitions() != null) {
            advancedParameters.put("partitions", index.partitions());
        }
        if (index.neighbors() != null) {
            advancedParameters.put("neighbors", index.neighbors());
        }
        if (index.efConstruction() != null) {
            advancedParameters.put("efConstruction", index.efConstruction());
        }
        if (!advancedParameters.isEmpty()) {
            indexParameters.set("advanced_params", advancedParameters);
        }

        return indexParameters.toString();
    }

    private static String toDatabaseValue(VecDbIndexOrganization organization) {
        return switch (organization) {
            case PARTITIONS -> "PARTITIONS";
            case INMEMORY_NEIGHBOR_GRAPH -> "INMEMORY GRAPH";
        };
    }
}
