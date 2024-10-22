package dev.langchain4j.store.embedding.vearch;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import dev.langchain4j.store.embedding.EmbeddingStoreWithoutMetadataIT;
import dev.langchain4j.store.embedding.vearch.field.*;
import dev.langchain4j.store.embedding.vearch.index.HNSWParam;
import dev.langchain4j.store.embedding.vearch.index.Index;
import dev.langchain4j.store.embedding.vearch.index.IndexType;
import dev.langchain4j.store.embedding.vearch.index.search.HNSWSearchParam;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static dev.langchain4j.store.embedding.vearch.TestUtils.isMethodFromClass;
import static org.assertj.core.api.Assertions.assertThat;

class VearchEmbeddingStoreIT extends EmbeddingStoreIT {

    static VearchContainer vearch = new VearchContainer();

    VearchEmbeddingStore embeddingStore;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    /**
     * in order to clear embedding store
     */
    static VearchClient vearchClient;

    static String databaseName;

    static String spaceName;

    static String baseUrl;

    @BeforeAll
    static void start() {
        vearch.start();

        databaseName = "embedding_db";
        spaceName = "embedding_space_" + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);
        baseUrl = "http://" + vearch.getHost() + ":" + vearch.getMappedPort(9001);
        vearchClient = VearchClient.builder()
                .baseUrl(baseUrl)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @AfterAll
    static void stop() {
        vearch.stop();
    }

    @BeforeEach
    void beforeEach(TestInfo testInfo) {
        if (isMethodFromClass(testInfo, EmbeddingStoreIT.class)) {
            buildEmbeddingStoreWithMetadata();
        } else if (isMethodFromClass(testInfo, EmbeddingStoreWithoutMetadataIT.class) || isMethodFromClass(testInfo, VearchEmbeddingStoreIT.class)) {
            buildEmbeddingStoreWithoutMetadata();
        }
    }

    private void buildEmbeddingStoreWithMetadata() {
        buildEmbeddingStore(true);
    }

    private void buildEmbeddingStoreWithoutMetadata() {
        buildEmbeddingStore(false);
    }

    private void buildEmbeddingStore(boolean withMetadata) {
        String embeddingFieldName = "text_embedding";
        String textFieldName = "text";
        Map<String, Object> metadata = createMetadata().toMap();

        // init fields
        List<Field> fields = new ArrayList<>(4);
        List<String> metadataFieldNames = new ArrayList<>();
        fields.add(VectorField.builder()
                .name(embeddingFieldName)
                .dimension(embeddingModel.dimension())
                .index(Index.builder()
                        .name("gamma")
                        .type(IndexType.HNSW)
                        .params(HNSWParam.builder()
                                .metricType(MetricType.INNER_PRODUCT)
                                .efConstruction(100)
                                .nLinks(32)
                                .efSearch(64)
                                .build())
                        .build())
                .build()
        );
        fields.add(StringField.builder().name(textFieldName).fieldType(FieldType.STRING).build());
        if (withMetadata) {
            // metadata
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof String || value instanceof UUID) {
                    fields.add(StringField.builder().name(key).fieldType(FieldType.STRING).build());
                } else if (value instanceof Integer) {
                    fields.add(NumericField.builder().name(key).fieldType(FieldType.INTEGER).build());
                } else if (value instanceof Long) {
                    fields.add(NumericField.builder().name(key).fieldType(FieldType.LONG).build());
                } else if (value instanceof Float) {
                    fields.add(NumericField.builder().name(key).fieldType(FieldType.FLOAT).build());
                } else if (value instanceof Double) {
                    fields.add(NumericField.builder().name(key).fieldType(FieldType.DOUBLE).build());
                }
            }
        }

        // init vearch config
        spaceName = "embedding_space_" + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);
        VearchConfig vearchConfig = VearchConfig.builder()
                .databaseName(databaseName)
                .spaceName(spaceName)
                .textFieldName(textFieldName)
                .embeddingFieldName(embeddingFieldName)
                .fields(fields)
                .metadataFieldNames(metadataFieldNames)
                .searchIndexParam(HNSWSearchParam.builder()
                        .metricType(MetricType.INNER_PRODUCT)
                        .efSearch(64)
                        .build())
                .build();
        if (withMetadata) {
            vearchConfig.setMetadataFieldNames(new ArrayList<>(metadata.keySet()));
        }

        // init embedding store
        embeddingStore = VearchEmbeddingStore.builder()
                .vearchConfig(vearchConfig)
                .baseUrl(baseUrl)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        vearchClient.deleteSpace(databaseName, spaceName);

        buildEmbeddingStoreWithMetadata();
    }

    @Override
    protected void ensureStoreIsEmpty() {
        // This method should be skipped because the @BeforeEach method of the parent class is called before the @BeforeEach method of the child class
        // This test manually create Space at @BeforeEach, so it's guaranteed that the EmbeddingStore is empty
    }

    @Override
    protected boolean testFloatAndDoubleExactly() {
        return false;
    }

    @Test
    void should_delete_space() {
        embeddingStore.deleteSpace();
        List<ListSpaceResponse> actual = vearchClient.listSpaceOfDatabase(databaseName);
        assertThat(actual.stream().map(ListSpaceResponse::getName)).doesNotContain(spaceName);
    }

}
