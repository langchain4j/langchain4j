package dev.langchain4j.store.embedding.vearch;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(DeleteSpaceLastOrderer.class)
@Testcontainers
class VearchEmbeddingStoreIT extends EmbeddingStoreIT {

    @Container
    static GenericContainer<?> vearch = new GenericContainer<>(DockerImageName.parse("vearch/vearch:3.4.1"))
            .withExposedPorts(9001, 8817)
            .withCommand("all")
            .withCopyFileToContainer(MountableFile.forClasspathResource("config.toml"), "/vearch/config.toml")
            .waitingFor(Wait.forLogMessage(".*INFO : server pid:1.*\\n", 1));

    VearchEmbeddingStore embeddingStore;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    /**
     * in order to clear embedding store
     */
    VearchClient vearchClient;

    String databaseName;

    String spaceName;

    VearchConfig vearchConfig;

    public VearchEmbeddingStoreIT() {
        String embeddingFieldName = "text_embedding";
        String textFieldName = "text";
        String metadataFieldName = "test-key";

        this.databaseName = "embedding_db";
        this.spaceName = "embedding_space";

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

        // init vearch config
        this.vearchConfig = VearchConfig.builder()
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
                .metadataFieldNames(singletonList(metadataFieldName))
                .build();

        // init embedding store and vearch client
        String baseUrl = "http://" + vearch.getHost() + ":" + vearch.getMappedPort(9001);
        embeddingStore = VearchEmbeddingStore.builder()
                .vearchConfig(this.vearchConfig)
                .baseUrl(baseUrl)
                .build();

        vearchClient = VearchClient.builder()
                .baseUrl(baseUrl)
                .timeout(Duration.ofSeconds(60))
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

        vearchClient.createSpace(databaseName, CreateSpaceRequest.builder()
                .name(spaceName)
                .engine(vearchConfig.getSpaceEngine())
                .replicaNum(1)
                .partitionNum(1)
                .properties(vearchConfig.getProperties())
                .models(vearchConfig.getModelParams())
                .build());
    }

    @Test
    void should_delete_space() {
        embeddingStore.deleteSpace();
        List<ListSpaceResponse> actual = vearchClient.listSpace(databaseName);
        assertThat(actual.stream().map(ListSpaceResponse::getName)).doesNotContain(spaceName);
    }

}
