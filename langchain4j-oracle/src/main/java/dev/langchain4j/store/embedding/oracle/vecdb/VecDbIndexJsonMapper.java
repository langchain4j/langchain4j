package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.store.embedding.oracle.CreateOption;

/**
 * Maps {@link VecDbIndexParameters} configuration to the {@code index_params} JSON accepted by
 * {@code DBMS_VECTOR_DATABASE}.
 */
final class VecDbIndexJsonMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private VecDbIndexJsonMapper() {}

    /**
     * Returns the VecDB {@code index_params} JSON for an index configuration.
     */
    static String toJson(VecDbIndexParameters parameters) {
        ensureNotNull(parameters, "indexParameters");

        ObjectNode indexParameters = OBJECT_MAPPER.createObjectNode();
        if (parameters.vectorIndex() != null) {
            indexParameters.set("vector_index_params", vectorIndexParameters(parameters.vectorIndex()));
        }
        if (parameters.metadataIndex() != null) {
            indexParameters.set("metadata_index_params", metadataIndexParameters(parameters.metadataIndex()));
        }
        if (parameters.parallelCreation() != null) {
            indexParameters.put("parallel_creation", parameters.parallelCreation());
        }

        return indexParameters.toString();
    }

    /** Returns parameters that drop every metadata index without affecting the vector index. */
    static String dropMetadataIndexesJson() {
        ObjectNode indexParameters = OBJECT_MAPPER.createObjectNode();
        indexParameters.put("index_type", "metadata");
        return indexParameters.toString();
    }

    private static ObjectNode vectorIndexParameters(VecDbVectorIndex vectorIndex) {

        ObjectNode vectorIndexParameters = OBJECT_MAPPER.createObjectNode();
        vectorIndexParameters.put("auto_index", vectorIndex.createOption() != CreateOption.CREATE_NONE);
        vectorIndexParameters.put("organization", toDatabaseValue(vectorIndex.organization()));
        vectorIndexParameters.put(
                "distance_metric", vectorIndex.distanceMetric().name());

        if (vectorIndex.accuracy() != null) {
            vectorIndexParameters.put("accuracy", vectorIndex.accuracy());
        }
        if (vectorIndex.quantizationType() != null) {
            vectorIndexParameters.put(
                    "quantization_type", vectorIndex.quantizationType().name());
        }
        if (vectorIndex.compressionRatio() != null) {
            vectorIndexParameters.put("compression_ratio", vectorIndex.compressionRatio());
        }
        if (vectorIndex.onlineBuild() != null) {
            vectorIndexParameters.put("online_build", vectorIndex.onlineBuild());
        }
        if (vectorIndex.distributeParameters() != null) {
            ObjectNode distributeParameters = OBJECT_MAPPER.createObjectNode();
            if (vectorIndex.distributeParameters().distributeMethod() != null) {
                distributeParameters.put(
                        "distribute_method", vectorIndex.distributeParameters().distributeMethod());
            }
            if (vectorIndex.distributeParameters().serviceName() != null) {
                distributeParameters.put(
                        "service_name", vectorIndex.distributeParameters().serviceName());
            }
            vectorIndexParameters.set("distribute_params", distributeParameters);
        }

        ObjectNode advancedParameters = OBJECT_MAPPER.createObjectNode();
        if (vectorIndex.partitions() != null) {
            advancedParameters.put("partitions", vectorIndex.partitions());
        }
        if (vectorIndex.neighbors() != null) {
            advancedParameters.put("neighbors", vectorIndex.neighbors());
        }
        if (vectorIndex.efConstruction() != null) {
            advancedParameters.put("efConstruction", vectorIndex.efConstruction());
        }
        if (vectorIndex.rescoreFactor() != null) {
            advancedParameters.put("rescore_factor", vectorIndex.rescoreFactor());
        }
        if (vectorIndex.quantizationAlgorithm() != null) {
            advancedParameters.put(
                    "algorithm", vectorIndex.quantizationAlgorithm().databaseValue());
        }
        if (!advancedParameters.isEmpty()) {
            vectorIndexParameters.set("advanced_params", advancedParameters);
        }

        return vectorIndexParameters;
    }

    private static ObjectNode metadataIndexParameters(VecDbMetadataIndex metadataIndex) {
        ObjectNode metadataIndexParameters = OBJECT_MAPPER.createObjectNode();
        boolean createMetadataIndex = metadataIndex.createOption() != CreateOption.CREATE_NONE;
        metadataIndexParameters.put("auto_index", createMetadataIndex && metadataIndex.autoIndex());

        if (createMetadataIndex && !metadataIndex.includePaths().isEmpty()) {
            metadataIndexParameters.set("include_paths", OBJECT_MAPPER.valueToTree(metadataIndex.includePaths()));
        }
        if (createMetadataIndex && !metadataIndex.excludePaths().isEmpty()) {
            metadataIndexParameters.set("exclude_paths", OBJECT_MAPPER.valueToTree(metadataIndex.excludePaths()));
        }

        return metadataIndexParameters;
    }

    private static String toDatabaseValue(VecDbIndexOrganization organization) {
        return switch (organization) {
            case PARTITIONS -> "PARTITIONS";
            case INMEMORY_NEIGHBOR_GRAPH -> "INMEMORY GRAPH";
        };
    }
}
