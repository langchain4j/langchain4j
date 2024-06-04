package dev.langchain4j.store.embedding.milvus;

import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import dev.langchain4j.store.embedding.milvus.parameter.IvfFlatIndexParam;
import dev.langchain4j.store.embedding.milvus.parameter.IvfPqIndexParam;
import io.milvus.param.IndexType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;


@Testcontainers
class MilvusEmbeddingStoreIndexParamTest {
    private static final String COLLECTION_NAME = "test_collection";

    @Container
    private static final MilvusContainer milvus = new MilvusContainer("milvusdb/milvus:v2.3.1")
            .withCreateContainerCmdModifier(cmd -> {
                cmd.withHostConfig(HostConfig.newHostConfig()
                        // This security opts needs to be configured;
                        // otherwise, the container will fail to start.
                        // Refer to the parameters from https://raw.githubusercontent.com/milvus-io/milvus/master/scripts/standalone_embed.sh.
                        .withSecurityOpts(Collections.singletonList("seccomp=unconfined"))
                        // By default, the Milvus Docker image doesn't expose the target port.
                        // Manual binding is required to expose it;
                        // otherwise, it will result in a failed detection during the test container inspection,
                        // leading to an inability to connect to the Milvus instance.
                        .withPortBindings(PortBinding.parse("19530:19530"), PortBinding.parse("9091:9091")
                        )
                );
            });

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
                    .host(milvus.getHost())
                    .port(milvus.getMappedPort(19530))
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
                    .host(milvus.getHost())
                    .port(milvus.getMappedPort(19530))
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
                    .host(milvus.getHost())
                    .port(milvus.getMappedPort(19530))
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
                    .host(milvus.getHost())
                    .port(milvus.getMappedPort(19530))
                    .collectionName(COLLECTION_NAME)
                    .dimension(384)
                    .indexType(IndexType.IVF_FLAT)
                    .build();
        }).withMessage("IndexParam is required for indexType IVF_FLAT cannot be null");
    }
}
