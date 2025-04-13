package dev.langchain4j.store.embedding.cassandra;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class CassandraEmbeddingStoreIT extends EmbeddingStoreIT {

    protected static final String KEYSPACE = "langchain4j";
    protected static final String TEST_INDEX = "test_embedding_store";
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(CassandraEmbeddingStoreIT.class);

    CassandraEmbeddingStore embeddingStore;

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    /**
     * It is required to clean the repository in between tests
     */
    @Override
    protected void clearStore() {
        ((CassandraEmbeddingStore) embeddingStore()).clear();
    }

    @Override
    protected Percentage percentage() {
        return withPercentage(6); // TODO figure out why difference is so big
    }

    @Test
    void should_retrieve_inserted_vector_by_ann() {
        String sourceSentence = "Testing is doubting !";
        Embedding sourceEmbedding = embeddingModel().embed(sourceSentence).content();
        TextSegment sourceTextSegment = TextSegment.from(sourceSentence);
        String id = embeddingStore().add(sourceEmbedding, sourceTextSegment);
        assertThat(id != null && !id.isEmpty()).isTrue();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(sourceEmbedding)
                .maxResults(10)
                .build();

        List<EmbeddingMatch<TextSegment>> embeddingMatches = embeddingStore.search(searchRequest).matches();
        assertThat(embeddingMatches).hasSize(1);

        EmbeddingMatch<TextSegment> embeddingMatch = embeddingMatches.get(0);
        assertThat(embeddingMatch.score()).isBetween(0d, 1d);
        assertThat(embeddingMatch.embeddingId()).isEqualTo(id);
        assertThat(embeddingMatch.embedding()).isEqualTo(sourceEmbedding);
        assertThat(embeddingMatch.embedded()).isEqualTo(sourceTextSegment);
    }

    @Test
    void should_retrieve_inserted_vector_by_ann_and_metadata() {
        String sourceSentence = "In GOD we trust, everything else we test!";
        Embedding sourceEmbedding = embeddingModel().embed(sourceSentence).content();
        TextSegment sourceTextSegment = TextSegment.from(sourceSentence, new Metadata()
                .put("user", "GOD")
                .put("test", "false"));
        String id = embeddingStore().add(sourceEmbedding, sourceTextSegment);
        assertThat(id != null && !id.isEmpty()).isTrue();

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(sourceEmbedding)
                .maxResults(10)
                .build();

        // Should be found with no filter
        List<EmbeddingMatch<TextSegment>> matchesAnnOnly = embeddingStore.search(searchRequest).matches();
        assertThat(matchesAnnOnly).hasSize(1);

        // Should retrieve if user is god
        List<EmbeddingMatch<TextSegment>> matchesGod = embeddingStore
                .findRelevant(sourceEmbedding, 10, .5d, Metadata.from("user", "GOD"));
        assertThat(matchesGod).hasSize(1);

        List<EmbeddingMatch<TextSegment>> matchesJohn = embeddingStore
                .findRelevant(sourceEmbedding, 10, .5d, Metadata.from("user", "JOHN"));
        assertThat(matchesJohn).isEmpty();
    }
}
