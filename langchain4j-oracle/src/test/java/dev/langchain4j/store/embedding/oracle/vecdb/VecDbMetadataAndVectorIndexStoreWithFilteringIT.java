package dev.langchain4j.store.embedding.oracle.vecdb;

import dev.langchain4j.store.embedding.oracle.CreateOption;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbDistanceMetric;

/**
 * Runs the LangChain4j filtering contract against a VecDB store configured with both a vector index
 * and automatic metadata indexing.
 */
class VecDbMetadataAndVectorIndexStoreWithFilteringIT extends OracleVecDbEmbeddingStoreWithFilteringIT {

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
}
