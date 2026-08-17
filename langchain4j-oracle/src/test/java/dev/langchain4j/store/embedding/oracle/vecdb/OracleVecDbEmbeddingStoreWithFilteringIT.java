package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import java.sql.SQLException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the core embedding-store and metadata-filtering contracts against VecDB.
 */
@Testcontainers(disabledWithoutDocker = true)
class OracleVecDbEmbeddingStoreWithFilteringIT extends EmbeddingStoreWithFilteringIT {

    protected static final String TABLE_NAME = "LC4J_VECDB_FILTER_IT";

    private OracleVecDbEmbeddingStore embeddingStore;

    @Override
    protected void ensureStoreIsReady() {
        if (embeddingStore == null) {
            embeddingStore = createEmbeddingStore();
        }
    }

    /** Creates the store configuration exercised by the inherited filtering contract. */
    protected OracleVecDbEmbeddingStore createEmbeddingStore() {
        return VecDbTestOperations.createStore(TABLE_NAME);
    }

    @Override
    protected void clearStore() {
        embeddingStore.removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return VecDbTestOperations.embeddingModel();
    }

    @Override
    protected boolean supportsContains() {
        return true;
    }

    /**
     * Verifies that the compatibility {@code text} metadata property can be filtered while it is
     * removed from the user-visible metadata reconstructed by search.
     */
    @Test
    void testFiltersByStoredTextMetadata() {
        TextSegment matchingSegment =
                TextSegment.from("Oracle VecDB integration", new Metadata().put("tenant", "acme"));
        TextSegment nonMatchingSegment =
                TextSegment.from("Oracle relational integration", new Metadata().put("tenant", "acme"));
        Embedding matchingEmbedding = embeddingModel().embed(matchingSegment).content();
        Embedding nonMatchingEmbedding =
                embeddingModel().embed(nonMatchingSegment).content();

        String matchingId = embeddingStore().add(matchingEmbedding, matchingSegment);
        embeddingStore().add(nonMatchingEmbedding, nonMatchingSegment);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(matchingEmbedding)
                .filter(metadataKey("text").containsString("VecDB"))
                .maxResults(10)
                .build();

        assertThat(embeddingStore().search(request).matches()).singleElement().satisfies(match -> {
            assertThat(match.embeddingId()).isEqualTo(matchingId);
            assertThat(match.embedded().text()).isEqualTo("Oracle VecDB integration");
            assertThat(match.embedded().metadata().toMap())
                    .containsEntry("tenant", "acme")
                    .doesNotContainKey("text");
        });
    }

    /**
     * Verifies that an empty {@code ContainsString} value follows Java
     * {@link String#contains(CharSequence)} semantics: it matches every stored string value,
     * including an empty string, but does not match a record where the metadata field is absent.
     */
    @Test
    void testEmptyContainsStringMatchesAllStoredStringValues() {
        TextSegment firstMatchingSegment = TextSegment.from("First document", new Metadata().put("category", "guide"));
        TextSegment secondMatchingSegment = TextSegment.from("Second document", new Metadata().put("category", ""));
        TextSegment nonMatchingSegment = TextSegment.from("Document without category");

        Embedding firstMatchingEmbedding =
                embeddingModel().embed(firstMatchingSegment).content();
        Embedding secondMatchingEmbedding =
                embeddingModel().embed(secondMatchingSegment).content();
        Embedding nonMatchingEmbedding =
                embeddingModel().embed(nonMatchingSegment).content();

        String firstMatchingId = embeddingStore().add(firstMatchingEmbedding, firstMatchingSegment);
        String secondMatchingId = embeddingStore().add(secondMatchingEmbedding, secondMatchingSegment);
        embeddingStore().add(nonMatchingEmbedding, nonMatchingSegment);

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(firstMatchingEmbedding)
                .filter(metadataKey("category").containsString(""))
                .maxResults(10)
                .build();

        assertThat(embeddingStore().search(request).matches())
                .extracting(match -> match.embeddingId())
                .containsExactlyInAnyOrder(firstMatchingId, secondMatchingId);
    }

    @AfterAll
    static void dropVectorTable() throws SQLException {
        VecDbTestOperations.dropVectorTable(TABLE_NAME);
    }
}
