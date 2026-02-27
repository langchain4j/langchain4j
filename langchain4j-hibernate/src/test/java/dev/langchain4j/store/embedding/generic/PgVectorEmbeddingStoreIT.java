package dev.langchain4j.store.embedding.generic;

import static dev.langchain4j.store.embedding.TestUtils.awaitUntilAsserted;
import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;
import static org.testcontainers.shaded.org.apache.commons.lang3.RandomUtils.nextInt;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.hibernate.DatabaseKind;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PgVectorEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    @Container
    static PostgreSQLContainer<?> pgVector = new PostgreSQLContainer<>("pgvector/pgvector:pg15");

    HibernateEmbeddingStore<?> embeddingStore;

    EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected void ensureStoreIsReady() {
        embeddingStore = HibernateEmbeddingStore.dynamicBuilder()
                .databaseKind(DatabaseKind.POSTGRESQL)
                .host(pgVector.getHost())
                .port(pgVector.getFirstMappedPort())
                .database("test")
                .user("test")
                .password("test")
                .table("test" + nextInt(1000, 2000))
                .dimension(384)
                .createTable(true)
                .dropTableFirst(true)
                .build();
    }

    @AfterEach
    void clearData() {
        if (embeddingStore != null) {
            embeddingStore.close();
        }
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
    protected boolean supportsContains() {
        return true;
    }

    @Test
    void test_escape_in() {
        TextSegment[] segments = new TextSegment[] {
            TextSegment.from("toEscape", Metadata.from(Map.of("text", "This must be escaped '"))),
            TextSegment.from("notEscape", Metadata.from(Map.of("text", "This does not require to be escaped")))
        };
        List<Embedding> embeddings = new ArrayList<>(segments.length);
        for (TextSegment segment : segments) {
            Embedding embedding = embeddingModel().embed(segment.text()).content();
            embeddings.add(embedding);
        }

        List<String> ids = embeddingStore().addAll(embeddings, Arrays.asList(segments));
        awaitUntilAsserted(() -> assertThat(getAllEmbeddings()).hasSameSizeAs(segments));

        // In filter escapes values as well
        Filter filterIN = metadataKey("text").isIn("This must be escaped '");
        EmbeddingSearchRequest inSearchRequest = EmbeddingSearchRequest.builder()
                .maxResults(1)
                .queryEmbedding(embeddings.get(0))
                .filter(filterIN)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore().search(inSearchRequest);
        EmbeddingMatch<TextSegment> match = searchResult.matches().get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(ids.get(0));

        // In filter escapes values as well
        Filter filterNotIN = metadataKey("text").isNotIn("This must be escaped '");
        EmbeddingSearchRequest notInSearchRequest = EmbeddingSearchRequest.builder()
                .maxResults(1)
                .queryEmbedding(embeddings.get(0))
                .filter(filterNotIN)
                .build();

        searchResult = embeddingStore().search(notInSearchRequest);
        match = searchResult.matches().get(0);
        // It must retrieve the second embedding
        assertThat(match.embeddingId()).isEqualTo(ids.get(1));
    }
}
