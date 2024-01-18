package dev.langchain4j.store.embedding.vearch;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import dev.langchain4j.store.embedding.vearch.api.space.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.singletonList;

public class VearchEmbeddingStoreIT extends EmbeddingStoreIT {

    static String configPath = VearchEmbeddingStoreIT.class.getClassLoader().getResource("config.toml").getPath();
    static GenericContainer<?> vearch = new GenericContainer<>(DockerImageName.parse("vearch/vearch:latest"))
            .withCommand("all")
            .withFileSystemBind(configPath, "/vearch/config.toml", BindMode.READ_ONLY);

    EmbeddingStore<TextSegment> embeddingStore;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    public VearchEmbeddingStoreIT() {
        String embeddingFieldName = "text_embedding";
        String textFieldName = "text";
        String metadataFieldName = "test-key";

        // init properties
        Map<String, SpacePropertyParam> properties = new HashMap<>(4);
        properties.put(embeddingFieldName, SpacePropertyParam.VectorParam.builder()
                .index(true)
                .storeType(SpaceStoreType.MEMORY_ONLY)
                .dimension(384)
                .build());
        properties.put(textFieldName, SpacePropertyParam.StringParam.builder().build());
        // metadata
        properties.put(metadataFieldName, SpacePropertyParam.StringParam.builder().build());

        VearchConfig config = VearchConfig.builder()
                .spaceEngine(SpaceEngine.builder()
                        .name("gamma")
                        .indexSize(1L)
                        .retrievalType(RetrievalType.FLAT)
                        .retrievalParam(RetrievalParam.HNSWParam.builder()
                                .metricType(MetricType.INNER_PRODUCT)
                                .nlinks(-1)
                                .efConstruction(-1)
                                .build())
                        .build())
                .properties(properties)
                .embeddingFieldName(embeddingFieldName)
                .textFieldName(textFieldName)
                .databaseName("embedding_db")
                .spaceName("embedding_space")
                .modelParams(singletonList(ModelParam.builder()
                        .modelId("vgg16")
                        .fields(singletonList("string"))
                        .out("feature")
                        .build()))
                .metadataFieldNames(singletonList(metadataFieldName))
                .build();

        embeddingStore = VearchEmbeddingStore.builder()
                .vearchConfig(config)
                .baseUrl("http://124.223.105.99:9001")
                .build();
    }

    @BeforeAll
    static void beforeAll() {
        vearch.setPortBindings(Arrays.asList("8817:8817", "9001:9001"));
        vearch.start();
    }

    @AfterAll
    static void afterAll() {
        vearch.stop();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }
}
