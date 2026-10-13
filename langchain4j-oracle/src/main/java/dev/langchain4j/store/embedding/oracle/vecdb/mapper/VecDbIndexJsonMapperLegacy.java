package dev.langchain4j.store.embedding.oracle.vecdb.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.store.embedding.oracle.CreateOption;
import dev.langchain4j.store.embedding.oracle.vecdb.VecDbMetadataIndex;
import dev.langchain4j.store.embedding.oracle.vecdb.VecDbVectorIndex;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbIndexOrganization;

/** Maps vector-index configuration to the flat {@code index_params} JSON used before Oracle Database 23.26.3. */
public final class VecDbIndexJsonMapperLegacy {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_IVF_PARTITIONS = 5;

    private VecDbIndexJsonMapperLegacy() {}

    public static String toJson(
            VecDbVectorIndex vectorIndex, VecDbMetadataIndex metadataIndex, Integer parallelCreation) {
        requireSupportedConfiguration(metadataIndex, parallelCreation);
        if (vectorIndex == null || vectorIndex.createOption() == CreateOption.CREATE_NONE) {
            return null;
        }
        requireSupportedConfiguration(vectorIndex);

        ObjectNode indexParameters = OBJECT_MAPPER.createObjectNode();
        indexParameters.put("indexing", "auto");
        indexParameters.put("organization", toDatabaseValue(vectorIndex.organization()));
        if (vectorIndex.distanceMetric() != null) {
            indexParameters.put("distance_metric", vectorIndex.distanceMetric().name());
        }
        if (vectorIndex.accuracy() != null) {
            indexParameters.put("accuracy", vectorIndex.accuracy());
        }

        ObjectNode advancedParameters = OBJECT_MAPPER.createObjectNode();
        if (vectorIndex.partitions() != null) {
            advancedParameters.put("partitions", vectorIndex.partitions());
        } else if (vectorIndex.organization() == VecDbIndexOrganization.PARTITIONS) {
            // Supplying custom legacy index_params replaces the package's complete default JSON.
            advancedParameters.put("partitions", DEFAULT_IVF_PARTITIONS);
        }
        if (vectorIndex.neighbors() != null) {
            advancedParameters.put("neighbors", vectorIndex.neighbors());
        }
        if (vectorIndex.efConstruction() != null) {
            advancedParameters.put("efConstruction", vectorIndex.efConstruction());
        }
        if (!advancedParameters.isEmpty()) {
            indexParameters.set("advanced_params", advancedParameters);
        }
        return indexParameters.toString();
    }

    public static String dropMetadataIndexesJson() {
        throw unsupported("metadata indexes");
    }

    private static void requireSupportedConfiguration(VecDbMetadataIndex metadataIndex, Integer parallelCreation) {
        if (metadataIndex != null && metadataIndex.createOption() != CreateOption.CREATE_NONE) {
            throw unsupported("metadata indexes");
        }
        if (parallelCreation != null) {
            throw unsupported("parallel index creation");
        }
    }

    private static void requireSupportedConfiguration(VecDbVectorIndex vectorIndex) {
        if (vectorIndex.quantizationType() != null) {
            throw unsupported("quantizationType");
        }
        if (vectorIndex.compressionRatio() != null) {
            throw unsupported("compressionRatio");
        }
        if (vectorIndex.onlineBuild() != null) {
            throw unsupported("onlineBuild");
        }
        if (vectorIndex.distributeParameters() != null) {
            throw unsupported("distributeParameters");
        }
        if (vectorIndex.rescoreFactor() != null) {
            throw unsupported("rescoreFactor");
        }
        if (vectorIndex.quantizationAlgorithm() != null) {
            throw unsupported("quantizationAlgorithm");
        }
    }

    private static String toDatabaseValue(VecDbIndexOrganization organization) {
        return switch (organization) {
            case PARTITIONS -> "PARTITIONS";
            case INMEMORY_NEIGHBOR_GRAPH -> "INMEMORY GRAPH";
        };
    }

    private static UnsupportedFeatureException unsupported(String feature) {
        return new UnsupportedFeatureException("VecDB " + feature + " require Oracle Database 23.26.3 or later");
    }
}
