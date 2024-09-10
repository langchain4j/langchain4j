package dev.langchain4j.store.embedding.vearch;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

@Testcontainers
class VearchEmbeddingStoreIT extends EmbeddingStoreIT {

    @Container
    static GenericContainer<?> vearch = new GenericContainer<>(DockerImageName.parse("vearch/vearch:3.4.1"))
            .withExposedPorts(9001, 8817)
            .withCommand("all")
            .withCopyFileToContainer(MountableFile.forClasspathResource("config.toml"), "/vearch/config.toml")
            .waitingFor(Wait.forLogMessage(".*INFO : server pid:1.*\\n", 1));

    static final UUID TEST_UUID = UUID.randomUUID();

    VearchEmbeddingStore embeddingStore;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    /**
     * in order to clear embedding store
     */
    VearchClient vearchClient;

    String databaseName;

    String spaceName;

    String baseUrl;

    public VearchEmbeddingStoreIT() {
        databaseName = "embedding_db";
        spaceName = "embedding_space";
        baseUrl = "http://" + vearch.getHost() + ":" + vearch.getMappedPort(9001);
        vearchClient = VearchClient.builder()
                .baseUrl(baseUrl)
                .timeout(Duration.ofSeconds(60))
                .build();
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
            // This for-loop requires EmbeddingStoreIT#createMetadata() follow naming pattern like below:
            // if a metadata is string or uuid, the key must start with "string"
            // if a metadata is integer, the key must start with "integer"
            // if a metadata is float, the key must start with "float"
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith("string") || key.startsWith("uuid")) {
                    properties.put(key, SpacePropertyParam.StringParam.builder().build());
                } else if (key.startsWith("integer")) {
                    properties.put(key, SpacePropertyParam.IntegerParam.builder().build());
                } else if (key.startsWith("float")) {
                    properties.put(key, SpacePropertyParam.FloatParam.builder().build());
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
                .databaseName(this.databaseName)
                .spaceName(this.spaceName)
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

    @Test
    void should_add_embedding_with_segment_with_metadata() {

        Metadata metadata = createMetadata();

        TextSegment segment = TextSegment.from("hello", metadata);
        Embedding embedding = embeddingModel().embed(segment.text()).content();

        String id = embeddingStore().add(embedding, segment);
        assertThat(id).isNotBlank();

        {
            // Not returned.
            TextSegment altSegment = TextSegment.from("hello?");
            Embedding altEmbedding = embeddingModel().embed(altSegment.text()).content();
            embeddingStore().add(altEmbedding, altSegment);
        }

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(embedding, 1);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);

        assertThat(match.embedded().text()).isEqualTo(segment.text());

        assertThat(match.embedded().metadata().getString("string_empty")).isEqualTo("");
        assertThat(match.embedded().metadata().getString("string_space")).isEqualTo(" ");
        assertThat(match.embedded().metadata().getString("string_abc")).isEqualTo("abc");

        assertThat(match.embedded().metadata().getUUID("uuid")).isEqualTo(TEST_UUID);

        assertThat(match.embedded().metadata().getInteger("integer_min")).isEqualTo(Integer.MIN_VALUE);
        assertThat(match.embedded().metadata().getInteger("integer_minus_1")).isEqualTo(-1);
        assertThat(match.embedded().metadata().getInteger("integer_0")).isEqualTo(0);
        assertThat(match.embedded().metadata().getInteger("integer_1")).isEqualTo(1);
        assertThat(match.embedded().metadata().getInteger("integer_max")).isEqualTo(Integer.MAX_VALUE);

        assertThat(match.embedded().metadata().getFloat("float_min")).isEqualTo(-Float.MAX_VALUE);
        assertThat(match.embedded().metadata().getFloat("float_minus_1")).isEqualTo(-1f);
        assertThat(match.embedded().metadata().getFloat("float_0")).isEqualTo(Float.MIN_VALUE);
        assertThat(match.embedded().metadata().getFloat("float_1")).isEqualTo(1f);
        assertThat(match.embedded().metadata().getFloat("float_123")).isEqualTo(1.23456789f);
        assertThat(match.embedded().metadata().getFloat("float_max")).isEqualTo(Float.MAX_VALUE);

        // new API
        assertThat(embeddingStore().search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(1)
                .build()).matches()).isEqualTo(relevant);
    }

    protected Metadata createMetadata() {
        // Vearch do not support long and double
        Metadata metadata = new Metadata();

        metadata.put("string_empty", "");
        metadata.put("string_space", " ");
        metadata.put("string_abc", "abc");

        metadata.put("uuid", TEST_UUID);

        metadata.put("integer_min", Integer.MIN_VALUE);
        metadata.put("integer_minus_1", -1);
        metadata.put("integer_0", 0);
        metadata.put("integer_1", 1);
        metadata.put("integer_max", Integer.MAX_VALUE);

        metadata.put("float_min", -Float.MAX_VALUE);
        metadata.put("float_minus_1", -1f);
        metadata.put("float_0", Float.MIN_VALUE);
        metadata.put("float_1", 1f);
        metadata.put("float_123", 1.23456789f);
        metadata.put("float_max", Float.MAX_VALUE);

        return metadata;
    }

}
