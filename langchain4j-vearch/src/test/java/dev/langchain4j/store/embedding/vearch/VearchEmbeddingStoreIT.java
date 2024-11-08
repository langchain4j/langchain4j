package dev.langchain4j.store.embedding.vearch;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import dev.langchain4j.store.embedding.EmbeddingStoreWithoutMetadataIT;
import org.junit.jupiter.api.*;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static java.util.Collections.singletonList;
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

    private boolean isMethodFromClass(TestInfo testInfo, Class<?> clazz) {
        try {
            Optional<Method> method = testInfo.getTestMethod();
            if (method.isPresent()) {
                String methodName = method.get().getName();
                return clazz.getDeclaredMethod(methodName) != null;
            }
            return false;
        } catch (NoSuchMethodException e) {
            return false;
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

        // init properties
        Map<String, SpacePropertyParam> properties = new HashMap<>(4);
        properties.put(embeddingFieldName, SpacePropertyParam.VectorParam.builder()
                .index(true)
                .storeType(SpaceStoreType.MEMORY_ONLY)
                .dimension(384)
                .build());
        properties.put(textFieldName, SpacePropertyParam.StringParam.builder().build());
        if (withMetadata) {
            // metadata
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof String || value instanceof UUID) {
                    properties.put(key, SpacePropertyParam.StringParam.builder().build());
                } else if (value instanceof Integer) {
                    properties.put(key, SpacePropertyParam.IntegerParam.builder().build());
                } else if (value instanceof Float) {
                    properties.put(key, SpacePropertyParam.FloatParam.builder().build());
                } else {
                    properties.put(key, SpacePropertyParam.StringParam.builder().build());
                }
            }
        }

        // init vearch config
        VearchConfig vearchConfig = VearchConfig.builder()
                .spaceEngine(SpaceEngine.builder()
                        .name("gamma")
                        .indexSize(1L)
                        .retrievalType(RetrievalType.FLAT)
                        .retrievalParam(RetrievalParam.FLAT.builder()
                                .build())
                        .build())
                .properties(properties)
                .embeddingFieldName(embeddingFieldName)
                .textFieldName(textFieldName)
                .databaseName(databaseName)
                .spaceName(spaceName)
                .modelParams(singletonList(ModelParam.builder()
                        .modelId("vgg16")
                        .fields(singletonList("string"))
                        .out("feature")
                        .build()))
                .build();
        if (withMetadata) {
            vearchConfig.setMetadataFieldNames(new ArrayList<>(metadata.keySet()));
        }

        // init embedding store
        embeddingStore = VearchEmbeddingStore.builder()
                .vearchConfig(vearchConfig)
                .baseUrl(baseUrl)
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

    protected void clearStore() {
        vearchClient.deleteSpace(databaseName, spaceName);
    }

    protected void ensureStoreIsEmpty() {
        // This method should be blocked because the @BeforeEach method of the parent class is called before the beforeEach method of the child class
        // This test manually create Space at @BeforeEach, so it's guaranteed that the EmbeddingStore is empty
    }

    @Test
    void should_delete_space() {
        embeddingStore.deleteSpace();
        List<ListSpaceResponse> actual = vearchClient.listSpace(databaseName);
        assertThat(actual.stream().map(ListSpaceResponse::getName)).doesNotContain(spaceName);
    }

}
