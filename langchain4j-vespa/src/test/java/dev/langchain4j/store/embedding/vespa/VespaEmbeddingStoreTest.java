package dev.langchain4j.store.embedding.vespa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.embedding.Embedding;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class VespaEmbeddingStoreTest {

    @Test
    void should_pass_all_builder_parameters_to_store_instance() throws Exception {
        // Given
        String expectedUrl = "https://test.vespa.ai";
        String expectedKeyPath = "/path/to/key.pem";
        String expectedCertPath = "/path/to/cert.pem";
        Duration expectedTimeout = Duration.ofSeconds(30);
        String expectedNamespace = "test-namespace";
        String expectedDocumentType = "test-document";
        String expectedClusterName = "test-cluster";
        String expectedRankProfile = "test-rank-profile";
        Integer expectedTargetHits = 25;
        Boolean expectedAvoidDups = false;
        Boolean expectedLogRequests = true;
        Boolean expectedLogResponses = true;

        // When
        VespaEmbeddingStore store = VespaEmbeddingStore.builder()
                .url(expectedUrl)
                .keyPath(expectedKeyPath)
                .certPath(expectedCertPath)
                .timeout(expectedTimeout)
                .namespace(expectedNamespace)
                .documentType(expectedDocumentType)
                .clusterName(expectedClusterName)
                .rankProfile(expectedRankProfile)
                .targetHits(expectedTargetHits)
                .avoidDups(expectedAvoidDups)
                .logRequests(expectedLogRequests)
                .logResponses(expectedLogResponses)
                .build();

        // Then - use reflection to verify all fields are set correctly
        assertThat(store).isNotNull();
        assertThat(getFieldValue(store, "url")).isEqualTo(expectedUrl);
        assertThat(getFieldValue(store, "keyPath")).isEqualTo(Paths.get(expectedKeyPath));
        assertThat(getFieldValue(store, "certPath")).isEqualTo(Paths.get(expectedCertPath));
        assertThat(getFieldValue(store, "timeout")).isEqualTo(expectedTimeout);
        assertThat(getFieldValue(store, "namespace")).isEqualTo(expectedNamespace);
        assertThat(getFieldValue(store, "documentType")).isEqualTo(expectedDocumentType);
        assertThat(getFieldValue(store, "clusterName")).isEqualTo(expectedClusterName);
        assertThat(getFieldValue(store, "rankProfile")).isEqualTo(expectedRankProfile);
        assertThat(getFieldValue(store, "targetHits")).isEqualTo(expectedTargetHits);
        assertThat(getFieldValue(store, "avoidDups")).isEqualTo(expectedAvoidDups);
        assertThat(getFieldValue(store, "logRequests")).isEqualTo(expectedLogRequests);
        assertThat(getFieldValue(store, "logResponses")).isEqualTo(expectedLogResponses);
    }

    @Test
    void should_use_default_values_when_builder_parameters_not_specified() throws Exception {
        // Given
        String expectedUrl = "https://test.vespa.ai";

        // When
        VespaEmbeddingStore store =
                VespaEmbeddingStore.builder().url(expectedUrl).build();

        // Then - verify defaults are used
        assertThat(store).isNotNull();
        assertThat(getFieldValue(store, "url")).isEqualTo(expectedUrl);
        assertThat(getFieldValue(store, "keyPath")).isNull();
        assertThat(getFieldValue(store, "certPath")).isNull();
        assertThat(getFieldValue(store, "timeout")).isEqualTo(Duration.ofSeconds(5));
        assertThat(getFieldValue(store, "namespace")).isEqualTo("namespace");
        assertThat(getFieldValue(store, "documentType")).isEqualTo("langchain4j");
        assertThat(getFieldValue(store, "clusterName")).isEqualTo("langchain4j");
        assertThat(getFieldValue(store, "rankProfile")).isEqualTo("langchain4j_relevance_score");
        assertThat(getFieldValue(store, "targetHits")).isEqualTo(10);
        assertThat(getFieldValue(store, "avoidDups")).isEqualTo(true);
        assertThat(getFieldValue(store, "logRequests")).isEqualTo(false);
        assertThat(getFieldValue(store, "logResponses")).isEqualTo(false);
    }

    @Test
    void should_throw_when_ids_and_embeddings_have_different_sizes() {
        // Given
        VespaEmbeddingStore store =
                VespaEmbeddingStore.builder().url("https://test.vespa.ai").build();
        Embedding embedding1 = Embedding.from(List.of(1f, 2f, 3f));
        Embedding embedding2 = Embedding.from(List.of(4f, 5f, 6f));

        // When + Then
        assertThatThrownBy(() -> store.addAll(List.of("id1"), List.of(embedding1, embedding2), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same size");
    }

    @Test
    void should_not_throw_or_call_server_when_input_is_empty() {
        // Given
        VespaEmbeddingStore store =
                VespaEmbeddingStore.builder().url("https://test.vespa.ai").build();

        // When + Then - empty input returns before any feeder/server interaction
        assertThatCode(() -> store.addAll(List.of(), List.of(), null)).doesNotThrowAnyException();
    }

    private Object getFieldValue(Object object, String fieldName) throws Exception {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(object);
    }
}
