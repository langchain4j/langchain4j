package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.store.embedding.milvus.parameter.IvfFlatIndexParam;
import dev.langchain4j.store.embedding.milvus.parameter.IvfPqIndexParam;
import io.milvus.param.IndexType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;


import static org.assertj.core.api.Assertions.*;

/**
 * @date 2024/4/12
 */
class MilvusEmbeddingStoreIndexParamTest {
    private static final String COLLECTION_NAME = "test_collection";

    MilvusEmbeddingStore embeddingStore;

    @AfterEach
    void drop_collection() {
        if (embeddingStore != null) {
            embeddingStore.dropCollection(COLLECTION_NAME);
        }
    }


    @Test
    void should_create_collection_with_default_param() {
        assertThatNoException().isThrownBy(() -> {
            embeddingStore = MilvusEmbeddingStore.builder()
                    .uri(System.getenv("MILVUS_URI"))
                    .token(System.getenv("MILVUS_API_KEY"))
                    .collectionName(COLLECTION_NAME)
                    .dimension(384)
                    .indexType(IndexType.FLAT)
                    .build();
        });
    }

    @Test
    void should_create_collection_with_param_index() {
        assertThatNoException().isThrownBy(() -> {
            IvfFlatIndexParam indexParam = IvfFlatIndexParam.builder()
                    .nlist(1024)
                    .build();
            embeddingStore = MilvusEmbeddingStore.builder()
                    .uri(System.getenv("MILVUS_URI"))
                    .token(System.getenv("MILVUS_API_KEY"))
                    .collectionName(COLLECTION_NAME)
                    .dimension(384)
                    .indexType(IndexType.IVF_FLAT)
                    .indexParam(indexParam)
                    .build();
        });
    }

    @Test
    void should_create_collection_with_optional_param_index() {
        assertThatNoException().isThrownBy(() -> {
            IvfPqIndexParam indexParam = IvfPqIndexParam.builder()
                    .nlist(1024)
                    .m(8)
                    .build();
            embeddingStore = MilvusEmbeddingStore.builder()
                    .uri(System.getenv("MILVUS_URI"))
                    .token(System.getenv("MILVUS_API_KEY"))
                    .collectionName(COLLECTION_NAME)
                    .dimension(384)
                    .indexType(IndexType.IVF_PQ)
                    .indexParam(indexParam)
                    .build();
        });
    }


    @Test
    void create_collection_missing_required_param() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> {
            embeddingStore = MilvusEmbeddingStore.builder()
                    .uri(System.getenv("MILVUS_URI"))
                    .token(System.getenv("MILVUS_API_KEY"))
                    .collectionName(COLLECTION_NAME)
                    .dimension(384)
                    .indexType(IndexType.IVF_FLAT)
                    .build();
        }).withMessage("IndexParam is required for indexType IVF_FLAT cannot be null");
    }
}
