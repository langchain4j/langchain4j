package dev.langchain4j.store.embedding.milvus;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static dev.langchain4j.store.embedding.filter.Filter.not;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static io.milvus.common.clientenum.ConsistencyLevelEnum.STRONG;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.Filter;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DescribeIndexResponse;
import io.milvus.grpc.IndexDescription;
import io.milvus.grpc.KeyValuePair;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.index.DescribeIndexParam;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.milvus.MilvusContainer;

@Testcontainers
class MilvusEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    static final String MILVUS_DOCKER_IMAGE = System.getProperty("milvus.image", "milvusdb/milvus:v2.6.26");

    static MilvusContainer milvusContainer() {
        // without these settings, Milvus 2.6 and later never reports healthy when started in a single container
        return new MilvusContainer(MILVUS_DOCKER_IMAGE)
                .withEnv("DEPLOY_MODE", "STANDALONE")
                .withEnv("MILVUS_MODE", "standalone")
                .withEnv("ETCD_USE_EMBED", "true")
                .withEnv("COMMON_STORAGETYPE", "local")
                .withCommand("milvus", "run", "standalone");
    }

    private static final String COLLECTION_NAME = "test_collection";

    private static final Map<String, Object> HNSW_CONSTRUCTION_PARAMETERS = Map.of("efConstruction", 200, "m", 16);
    private static final Map<String, Object> IVFPQ_PARAMETERS = Map.of("m", 8, "nlist", 1024);

    @Container
    private static final MilvusContainer milvus = milvusContainer();

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
            .vectorFieldName("vector_field")
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
    void should_not_retrieve_embeddings_when_searching() {

        EmbeddingStore<TextSegment> embeddingStore = MilvusEmbeddingStore.builder()
                .host(milvus.getHost())
                .port(milvus.getMappedPort(19530))
                .collectionName(COLLECTION_NAME)
                .consistencyLevel(STRONG)
                .dimension(384)
                .retrieveEmbeddingsOnSearch(false)
                .idFieldName("id_field")
                .textFieldName("text_field")
                .metadataFieldName("metadata_field")
                .vectorFieldName("vector_field")
                .build();

        Embedding firstEmbedding = embeddingModel.embed("hello").content();
        Embedding secondEmbedding = embeddingModel.embed("hi").content();
        embeddingStore.addAll(asList(firstEmbedding, secondEmbedding));

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(firstEmbedding)
                        .maxResults(10)
                        .build())
                .matches();
        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).embedding()).isNull();
        assertThat(matches.get(1).embedding()).isNull();
    }

    @Test
    void should_retrieve_embeddings_for_ids_containing_special_characters() {

        Map<String, Embedding> embeddingsById = Map.of(
                "normal-id", embeddingModel.embed("hello").content(),
                "id'with'apostrophe", embeddingModel.embed("hi").content(),
                "id\"with-quote", embeddingModel.embed("hey").content(),
                "id\\with-backslash", embeddingModel.embed("howdy").content());

        embeddingsById.forEach(embeddingStore::add);

        // retrieveEmbeddingsOnSearch(true) queries Milvus a second time by these ids to fetch the vectors,
        // which only works when the ids are quoted and escaped in the query expression
        List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embeddingModel.embed("hello").content())
                        .maxResults(10)
                        .build())
                .matches();

        assertThat(matches).hasSize(embeddingsById.size());
        assertThat(matches)
                .allSatisfy(match -> assertThat(match.embedding()).isEqualTo(embeddingsById.get(match.embeddingId())));
    }

    @Test
    void milvus_with_existing_client() {

        ConnectParam.Builder connectBuilder = ConnectParam.newBuilder()
                .withHost(milvus.getHost())
                .withUri(milvus.getEndpoint())
                .withPort(milvus.getMappedPort(19530));

        MilvusServiceClient milvusServiceClient = new MilvusServiceClient(connectBuilder.build());

        EmbeddingStore<TextSegment> embeddingStore = MilvusEmbeddingStore.builder()
                .milvusClient(milvusServiceClient)
                .collectionName(COLLECTION_NAME)
                .consistencyLevel(STRONG)
                .dimension(384)
                .retrieveEmbeddingsOnSearch(false)
                .idFieldName("id_field")
                .textFieldName("text_field")
                .metadataFieldName("metadata_field")
                .vectorFieldName("vector_field")
                .build();

        Embedding firstEmbedding = embeddingModel.embed("hello").content();
        Embedding secondEmbedding = embeddingModel.embed("hi").content();
        embeddingStore.addAll(asList(firstEmbedding, secondEmbedding));

        List<EmbeddingMatch<TextSegment>> matches = embeddingStore
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(firstEmbedding)
                        .maxResults(10)
                        .build())
                .matches();
        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).embedding()).isNull();
        assertThat(matches.get(1).embedding()).isNull();
    }

    @Test
    void milvus_hnsw_index_with_custom_params() {
        ConnectParam.Builder connectBuilder = ConnectParam.newBuilder()
                .withHost(milvus.getHost())
                .withUri(milvus.getEndpoint())
                .withPort(milvus.getMappedPort(19530));

        MilvusServiceClient milvusServiceClient = new MilvusServiceClient(connectBuilder.build());

        embeddingStore.dropCollection(COLLECTION_NAME);

        MilvusEmbeddingStore.builder()
                .milvusClient(milvusServiceClient)
                .collectionName(COLLECTION_NAME)
                .consistencyLevel(STRONG)
                .dimension(384)
                .retrieveEmbeddingsOnSearch(false)
                .idFieldName("id_field")
                .textFieldName("text_field_hnsw")
                .metadataFieldName("metadata_field")
                .vectorFieldName("vector_field_hnsw")
                .indexType(IndexType.HNSW)
                .extraParameters(HNSW_CONSTRUCTION_PARAMETERS)
                .metricType(MetricType.COSINE)
                .build();

        final R<DescribeIndexResponse> indexDescribeResponse =
                milvusServiceClient.describeIndex(DescribeIndexParam.newBuilder()
                        .withCollectionName(COLLECTION_NAME)
                        .build());

        assertThat(indexDescribeResponse.getData().getIndexDescriptionsList()).hasSize(1);
        final IndexDescription indexDescription =
                indexDescribeResponse.getData().getIndexDescriptions(0);
        assertThat(indexDescription.getIndexName()).isEqualTo("vector_field_hnsw");

        // Verify that the extra parameters were set correctly
        final List<KeyValuePair> params = indexDescription.getParamsList();
        assertThat(params).isNotEmpty();
        // Check that the M and efConstruction parameters are present
        Map<String, String> paramMap = new HashMap<>();
        for (KeyValuePair param : params) {
            paramMap.put(param.getKey(), param.getValue());
        }
        String key = "efConstruction";
        assertThat(paramMap)
                .containsEntry(key, HNSW_CONSTRUCTION_PARAMETERS.get(key).toString());
        key = "m";
        assertThat(paramMap)
                .containsEntry(key, HNSW_CONSTRUCTION_PARAMETERS.get(key).toString());
    }

    @Test
    void milvus_ivfpq_index_with_custom_params() {
        ConnectParam.Builder connectBuilder = ConnectParam.newBuilder()
                .withHost(milvus.getHost())
                .withUri(milvus.getEndpoint())
                .withPort(milvus.getMappedPort(19530));

        MilvusServiceClient milvusServiceClient = new MilvusServiceClient(connectBuilder.build());

        embeddingStore.dropCollection(COLLECTION_NAME);

        MilvusEmbeddingStore.builder()
                .milvusClient(milvusServiceClient)
                .collectionName(COLLECTION_NAME)
                .consistencyLevel(STRONG)
                .dimension(384)
                .retrieveEmbeddingsOnSearch(false)
                .idFieldName("id_field")
                .textFieldName("text_field_hnsw")
                .metadataFieldName("metadata_field")
                .vectorFieldName("vector_field_ivfpq")
                .indexType(IndexType.IVF_PQ)
                .extraParameters(IVFPQ_PARAMETERS)
                .metricType(MetricType.COSINE)
                .build();

        final R<DescribeIndexResponse> indexDescribeResponse =
                milvusServiceClient.describeIndex(DescribeIndexParam.newBuilder()
                        .withCollectionName(COLLECTION_NAME)
                        .build());

        assertThat(indexDescribeResponse.getData().getIndexDescriptionsList()).hasSize(1);
        final IndexDescription indexDescription =
                indexDescribeResponse.getData().getIndexDescriptions(0);
        assertThat(indexDescription.getIndexName()).isEqualTo("vector_field_ivfpq");

        // Verify that the extra parameters were set correctly
        final List<KeyValuePair> params = indexDescription.getParamsList();
        assertThat(params).isNotEmpty();
        // Check that the M and efConstruction parameters are present
        Map<String, String> paramMap = new HashMap<>();
        for (KeyValuePair param : params) {
            paramMap.put(param.getKey(), param.getValue());
        }
        String key = "nlist";
        assertThat(paramMap).containsEntry(key, IVFPQ_PARAMETERS.get(key).toString());
        key = "m";
        assertThat(paramMap).containsEntry(key, IVFPQ_PARAMETERS.get(key).toString());
    }

    @Override
    protected boolean supportsContains() {
        return true;
    }

    @Test
    void escapeCharacterShouldBeUsed() {
        Embedding embedding = embeddingModel().embed("hello").content();
        embeddingStore().add(embedding);
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSize(1));

        Embedding referenceEmbedding = embeddingModel().embed("hi").content();

        Filter filter = metadataKey("key").isEqualTo("foo\"");

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(referenceEmbedding)
                .maxResults(1)
                .filter(filter)
                .build();

        embeddingStore().search(searchRequest);
    }

    @ParameterizedTest
    @MethodSource
    void should_match_entries_without_the_key_inside_not(
            Filter metadataFilter, List<Metadata> matchingMetadatas, List<Metadata> notMatchingMetadatas) {
        should_filter_by_metadata_not(metadataFilter, matchingMetadatas, notMatchingMetadatas);
    }

    static Stream<Arguments> should_match_entries_without_the_key_inside_not() {
        return Stream.of(
                Arguments.of(
                        not(metadataKey("age").isGreaterThan(40)),
                        List.of(new Metadata().put("age", 30), new Metadata().put("name", "a"), new Metadata()),
                        List.of(new Metadata().put("age", 50))),
                Arguments.of(
                        not(metadataKey("name").isIn("a", "b")),
                        List.of(new Metadata().put("name", "c"), new Metadata().put("age", 1), new Metadata()),
                        List.of(new Metadata().put("name", "a"), new Metadata().put("name", "b"))),
                Arguments.of(
                        not(metadataKey("name").isEqualTo("a").or(metadataKey("age").isGreaterThan(40))),
                        List.of(
                                new Metadata().put("name", "b").put("age", 30),
                                new Metadata().put("other", 1),
                                new Metadata()),
                        List.of(new Metadata().put("name", "a"), new Metadata().put("age", 50))),
                Arguments.of(
                        not(metadataKey("name").isEqualTo("a").and(metadataKey("age").isNotEqualTo(30))),
                        List.of(
                                new Metadata().put("name", "b"),
                                new Metadata().put("name", "a").put("age", 30),
                                new Metadata().put("age", 50),
                                new Metadata()),
                        List.of(new Metadata().put("name", "a"), new Metadata().put("name", "a").put("age", 50))));
    }
}
