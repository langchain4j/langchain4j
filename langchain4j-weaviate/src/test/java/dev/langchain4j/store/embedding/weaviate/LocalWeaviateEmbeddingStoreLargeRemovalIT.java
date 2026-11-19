package dev.langchain4j.store.embedding.weaviate;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import io.weaviate.client6.v1.api.WeaviateClient;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.weaviate.WeaviateContainer;

/**
 * Removes more objects than Weaviate removes or returns per request (QUERY_MAXIMUM_RESULTS),
 * on the oldest supported Weaviate version and on the latest one.
 */
class LocalWeaviateEmbeddingStoreLargeRemovalIT {

    private static final int MAXIMUM_RESULTS = 500;
    private static final int COUNT = 1200;

    @ParameterizedTest
    @ValueSource(strings = {"1.32.0", "latest"})
    void should_remove_more_objects_than_the_maximum_results(String version) throws Exception {
        try (WeaviateContainer weaviate = new WeaviateContainer("semitechnologies/weaviate:" + version)
                .withEnv("QUERY_MAXIMUM_RESULTS", String.valueOf(MAXIMUM_RESULTS))
                .withEnv("CLUSTER_HOSTNAME", "node1")) {
            weaviate.start();

            try (WeaviateClient client = WeaviateClient.connectToLocal(local -> local.host(weaviate.getHost())
                    .port(weaviate.getFirstMappedPort())
                    .grpcPort(weaviate.getMappedPort(50051)))) {

                for (String metadataFieldName : List.of("_metadata", "")) {
                    String objectClass = metadataFieldName.isEmpty() ? "RootMetadata" : "NestedMetadata";
                    try (WeaviateEmbeddingStore store = WeaviateEmbeddingStore.builder()
                            .scheme("http")
                            .host(weaviate.getHost())
                            .port(weaviate.getFirstMappedPort())
                            .grpcPort(weaviate.getMappedPort(50051))
                            .objectClass(objectClass)
                            .metadataKeys(List.of("type"))
                            .metadataFieldName(metadataFieldName)
                            .build()) {

                        List<Embedding> embeddings = new ArrayList<>();
                        List<TextSegment> segments = new ArrayList<>();
                        for (int i = 0; i < COUNT; i++) {
                            embeddings.add(Embedding.from(new float[] {i + 1, 1, 2}));
                            segments.add(TextSegment.from("text " + i, Metadata.from("type", i % 3 == 0 ? "a" : "b")));
                        }
                        store.addAll(embeddings, segments);
                        assertThat(client.collections.use(objectClass).size()).isEqualTo(COUNT);

                        store.removeAll(metadataKey("type").isEqualTo("b"));

                        assertThat(client.collections.use(objectClass).size())
                                .as("%s, %s", version, objectClass)
                                .isEqualTo(COUNT / 3);
                        assertThat(store.search(EmbeddingSearchRequest.builder()
                                                .queryEmbedding(embeddings.get(0))
                                                .maxResults(10)
                                                .build())
                                        .matches())
                                .isNotEmpty()
                                .allSatisfy(match -> {
                                    assertThat(match.embedded().text()).startsWith("text ");
                                    assertThat(match.embedded().metadata().getString("type"))
                                            .isEqualTo("a");
                                });

                        store.addAll(embeddings.subList(0, 2 * MAXIMUM_RESULTS + 100));
                        store.removeAll();

                        assertThat(client.collections.use(objectClass).size())
                                .as("%s, %s", version, objectClass)
                                .isZero();
                    }
                }
            }
        }
    }
}
