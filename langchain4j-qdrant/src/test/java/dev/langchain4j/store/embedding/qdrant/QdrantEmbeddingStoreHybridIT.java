package dev.langchain4j.store.embedding.qdrant;

import static dev.langchain4j.internal.Utils.randomUUID;
import static io.qdrant.client.grpc.Collections.Distance.Cosine;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.CreateCollection;
import io.qdrant.client.grpc.Collections.SparseVectorConfig;
import io.qdrant.client.grpc.Collections.SparseVectorParams;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Collections.VectorParamsMap;
import io.qdrant.client.grpc.Collections.VectorsConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.qdrant.QdrantContainer;

@Testcontainers
class QdrantEmbeddingStoreHybridIT {

    private static final String COLLECTION_NAME = "langchain4j-hybrid-" + randomUUID();

    @Container
    private static final QdrantContainer QDRANT_CONTAINER = new QdrantContainer("qdrant/qdrant:latest");

    private static QdrantEmbeddingStore store;
    private static final EmbeddingModel EMBEDDING_MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void setup() throws Exception {
        QdrantClient client = new QdrantClient(QdrantGrpcClient.newBuilder(
                        QDRANT_CONTAINER.getHost(), QDRANT_CONTAINER.getGrpcPort(), false)
                .build());

        CreateCollection request = CreateCollection.newBuilder()
                .setCollectionName(COLLECTION_NAME)
                .setVectorsConfig(VectorsConfig.newBuilder()
                        .setParamsMap(VectorParamsMap.newBuilder()
                                .putMap(
                                        "dense",
                                        VectorParams.newBuilder()
                                                .setDistance(Cosine)
                                                .setSize(EMBEDDING_MODEL.dimension())
                                                .build())
                                .build())
                        .build())
                .setSparseVectorsConfig(SparseVectorConfig.newBuilder()
                        .putMap("sparse", SparseVectorParams.getDefaultInstance())
                        .build())
                .build();
        client.createCollectionAsync(request).get();

        store = QdrantEmbeddingStore.builder()
                .client(client)
                .collectionName(COLLECTION_NAME)
                .searchMode(SearchMode.HYBRID)
                .sparseEncoder(new HashingSparseEncoder())
                .build();
    }

    @AfterAll
    static void teardown() {
        if (store != null) {
            store.close();
        }
    }

    @Test
    void should_find_document_by_exact_keyword_using_sparse_signal() {
        List<String> texts = List.of(
                "Cats are small domestic carnivorous mammals.",
                "Dogs are loyal pets kept in many households.",
                "Roses bloom in spring and come in many colours.",
                "Volcanoes form where tectonic plates diverge or collide.",
                "The xylophone rutabaga is a made-up marker phrase.");

        addTexts(texts, null);

        String query = "xylophone rutabaga";
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .query(query)
                .queryEmbedding(EMBEDDING_MODEL.embed(query).content())
                .maxResults(3)
                .build();

        List<EmbeddingMatch<TextSegment>> matches =
                store.search(request).matches();

        assertThat(matches).isNotEmpty();
        assertThat(matches.get(0).embedded().text()).contains("xylophone rutabaga");
        // RRF scores are small positive numbers.
        assertThat(matches.get(0).score()).isGreaterThan(0.0);
    }

    @Test
    void should_respect_filter_in_hybrid_mode() {
        List<String> texts = List.of(
                "Alpha keyword alpha filterable",
                "Alpha keyword alpha unfilterable",
                "Beta text has no keyword");
        List<String> tags = List.of("include", "exclude", "include");

        addTexts(texts, tags);

        String query = "alpha keyword";
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .query(query)
                .queryEmbedding(EMBEDDING_MODEL.embed(query).content())
                .maxResults(5)
                .filter(MetadataFilterBuilder.metadataKey("tag").isEqualTo("include"))
                .build();

        List<EmbeddingMatch<TextSegment>> matches =
                store.search(request).matches();

        assertThat(matches).isNotEmpty();
        assertThat(matches).allSatisfy(m ->
                assertThat(m.embedded().metadata().getString("tag")).isEqualTo("include"));
    }

    @Test
    void should_throw_when_hybrid_search_without_query_text() {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(EMBEDDING_MODEL.embed("anything").content())
                .maxResults(1)
                .build();

        assertThatThrownBy(() -> store.search(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query");
    }

    @Test
    void should_throw_when_hybrid_addAll_missing_text_segments() {
        List<String> ids = List.of(randomUUID());
        List<Embedding> embeddings = List.of(EMBEDDING_MODEL.embed("foo").content());

        assertThatThrownBy(() -> store.addAll(ids, embeddings, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HYBRID");
    }

    private static void addTexts(List<String> texts, List<String> tagValues) {
        List<TextSegment> segments = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            TextSegment seg = tagValues != null
                    ? TextSegment.from(texts.get(i), dev.langchain4j.data.document.Metadata.from("tag", tagValues.get(i)))
                    : TextSegment.from(texts.get(i));
            segments.add(seg);
        }
        List<Embedding> embeddings = EMBEDDING_MODEL.embedAll(segments).content();
        List<String> ids = texts.stream().map(t -> randomUUID()).toList();
        store.addAll(ids, embeddings, segments);
    }

    /** Deterministic hashing sparse encoder for tests only. */
    static class HashingSparseEncoder implements SparseEncoder {
        private static final int VOCAB = 4096;

        @Override
        public SparseVector encode(String text) {
            Map<Integer, Float> counts = new LinkedHashMap<>();
            for (String token : text.toLowerCase().split("\\W+")) {
                if (token.isEmpty()) continue;
                int idx = (token.hashCode() & 0x7FFFFFFF) % VOCAB;
                counts.merge(idx, 1f, Float::sum);
            }
            if (counts.isEmpty()) {
                counts.put(0, 1f);
            }
            return new SparseVector(new ArrayList<>(counts.keySet()), new ArrayList<>(counts.values()));
        }
    }
}
