package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DescribeIndexResponse;
import io.milvus.grpc.IndexDescription;
import io.milvus.grpc.KeyValuePair;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.index.DescribeIndexParam;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.milvus.common.clientenum.ConsistencyLevelEnum.STRONG;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MilvusEmbeddingStoreHNSWIindexIT extends EmbeddingStoreWithFilteringIT {

    static final String MILVUS_DOCKER_IMAGE = "milvusdb/milvus:v2.5.10";

    private static final String COLLECTION_NAME = "test_collection";

    private static final AbstractMap.SimpleEntry<String, Integer> EF_CONSTRUCTION_PARAM =
            new AbstractMap.SimpleEntry<>("efConstruction", 200);
    private static final AbstractMap.SimpleEntry<String, Integer> M_PARAM =
            new AbstractMap.SimpleEntry<>("M", 16);

    @Container

    private static final MilvusContainer milvus = new MilvusContainer(MILVUS_DOCKER_IMAGE);

    MilvusEmbeddingStore embeddingStore = MilvusEmbeddingStore.builder()
            .uri(milvus.getEndpoint())
            .collectionName(COLLECTION_NAME)
            .consistencyLevel(STRONG)
            .username(System.getenv("MILVUS_USERNAME"))
            .password(System.getenv("MILVUS_PASSWORD"))
            .dimension(384)
            .retrieveEmbeddingsOnSearch(true)
            .idFieldName("id_field")
            .textFieldName("text_field")
            .metadataFieldName("metadata_field")
            .vectorFieldName("vector_field_hnsw")
            .indexType(IndexType.HNSW)
            .extraParam(M_PARAM.getKey(), M_PARAM.getValue())
            .extraParam(EF_CONSTRUCTION_PARAM.getKey(), EF_CONSTRUCTION_PARAM.getValue())
            .metricType(MetricType.COSINE)
            .build();

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @AfterEach
    void afterEach() {
        embeddingStore.dropCollection(COLLECTION_NAME);
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Test
    void milvus_index_with_custom_params() {
        ConnectParam.Builder connectBuilder = ConnectParam.newBuilder()
                .withHost(milvus.getHost())
                .withUri(milvus.getEndpoint())
                .withPort(milvus.getMappedPort(19530))
                .withAuthorization("", "");

        MilvusServiceClient milvusServiceClient = new MilvusServiceClient(connectBuilder.build());

        final R<DescribeIndexResponse> indexDescribeResponse = milvusServiceClient
                .describeIndex(DescribeIndexParam.newBuilder()
                        .withCollectionName(COLLECTION_NAME)
                        .build());

        assertThat(indexDescribeResponse.getData().getIndexDescriptionsList()).hasSize(1);
        final IndexDescription indexDescription = indexDescribeResponse.getData().getIndexDescriptions(0);
        assertThat(indexDescription.getIndexName()).isEqualTo("vector_field_hnsw");

        // Verify that the extra parameters were set correctly
        final List<KeyValuePair> params = indexDescription.getParamsList();
        assertThat(params).isNotEmpty();
        // Check that the M and efConstruction parameters are present
        Map<String, String> paramMap = new HashMap<>();
        for (KeyValuePair param : params) {
            paramMap.put(param.getKey(), param.getValue());
        }
        assertThat(paramMap).containsEntry(M_PARAM.getKey(), M_PARAM.getValue().toString());
        assertThat(paramMap).containsEntry(EF_CONSTRUCTION_PARAM.getKey(), EF_CONSTRUCTION_PARAM.getValue().toString());

        Embedding firstEmbedding = embeddingModel().embed("hello").content();
        Embedding secondEmbedding = embeddingModel().embed("hi").content();
        embeddingStore.addAll(asList(firstEmbedding, secondEmbedding));

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(firstEmbedding)
                        .maxResults(10)
                        .build())
                .matches();
        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).embedding()).isNotNull();
        assertThat(matches.get(1).embedding()).isNotNull();
    }

    @Override
    protected boolean supportsContains() {
        return true;
    }
}
