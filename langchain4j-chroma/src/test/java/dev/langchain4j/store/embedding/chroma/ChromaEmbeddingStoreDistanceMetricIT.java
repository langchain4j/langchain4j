package dev.langchain4j.store.embedding.chroma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.chromadb.ChromaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ChromaEmbeddingStoreDistanceMetricIT {

    @Container
    private static final ChromaDBContainer chroma = new ChromaDBContainer("chromadb/chroma:0.5.4");

    @Test
    void should_throw_when_existing_collection_uses_a_non_cosine_distance_metric() throws Exception {
        createCollection("l2_collection", "l2");

        assertThatThrownBy(() -> store("l2_collection"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Chroma collection 'l2_collection' uses distance metric 'l2', but ChromaEmbeddingStore "
                        + "requires 'cosine' to produce valid relevance scores. Use or recreate a collection "
                        + "configured with cosine distance.");
    }

    @Test
    void should_accept_an_existing_collection_created_by_this_store() {
        store("created_by_store");

        assertThatCode(() -> store("created_by_store")).doesNotThrowAnyException();
    }

    private static ChromaEmbeddingStore store(String collectionName) {
        return ChromaEmbeddingStore.builder()
                .baseUrl(chroma.getEndpoint())
                .collectionName(collectionName)
                .build();
    }

    private static void createCollection(String name, String space) throws IOException {
        ChromaHttpClient httpClient = new ChromaHttpClient(chroma.getEndpoint(), Duration.ofSeconds(10), false, false);

        Collection created = httpClient.post(
                "api/v1/collections", Map.of("name", name, "metadata", Map.of("hnsw:space", space)), Collection.class);

        assertThat(created.distanceFunction()).isEqualTo(space);
    }
}
