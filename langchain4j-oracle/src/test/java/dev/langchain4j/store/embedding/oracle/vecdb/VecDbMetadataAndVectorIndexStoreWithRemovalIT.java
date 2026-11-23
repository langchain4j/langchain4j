package dev.langchain4j.store.embedding.oracle.vecdb;

import dev.langchain4j.store.embedding.oracle.CreateOption;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbApiVersion;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbDistanceMetric;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Runs the LangChain4j removal contract against a VecDB store configured with both a vector index
 * and automatic metadata indexing.
 */
@EnabledIf("supportMetadataIndex")
class VecDbMetadataAndVectorIndexStoreWithRemovalIT extends OracleVecDbEmbeddingStoreWithRemovalIT {

    @Override
    protected OracleVecDbEmbeddingStore createEmbeddingStore() {
        VecDbVectorIndex vectorIndex = VecDbVectorIndex.ivfIndexBuilder()
                .createOption(CreateOption.CREATE_OR_REPLACE)
                .distanceMetric(VecDbDistanceMetric.COSINE)
                .build();
        VecDbMetadataIndex metadataIndex = VecDbMetadataIndex.builder()
                .createOption(CreateOption.CREATE_OR_REPLACE)
                .autoIndex(true)
                .build();

        return OracleVecDbEmbeddingStore.builder()
                .dataSource(VecDbTestOperations.dataSource())
                .embeddingTable(TABLE_NAME, CreateOption.CREATE_OR_REPLACE)
                .index(vectorIndex)
                .metadataIndex(metadataIndex)
                .distanceMetric(VecDbDistanceMetric.COSINE)
                .build();
    }

    private static boolean supportMetadataIndex() {
        return VecDbTestOperations.apiVersion() == VecDbApiVersion.V23_26_3;
    }
}
