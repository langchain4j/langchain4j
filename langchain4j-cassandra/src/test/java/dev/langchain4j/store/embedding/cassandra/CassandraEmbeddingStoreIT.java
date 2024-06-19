package dev.langchain4j.store.embedding.cassandra;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiModelName;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import dev.langchain4j.store.embedding.RelevanceScore;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.Utils.randomUUID;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
abstract class CassandraEmbeddingStoreIT extends EmbeddingStoreIT {

    protected static final String KEYSPACE = "langchain4j";

    protected static final String TEST_INDEX = "test_embedding_store";

    CassandraEmbeddingStore embeddingStore;

    EmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName(OpenAiModelName.TEXT_EMBEDDING_ADA_002)
            .timeout(Duration.ofSeconds(15))
            .build();

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    protected int embeddingModelDimension() {
        return 1536;
    }

    /**
     * It is required to clean the repository in between tests
     */
    @Override
    protected void clearStore() {
        ((CassandraEmbeddingStore) embeddingStore()).clear();
    }

    @Override
    public void awaitUntilPersisted() {
        try {
            Thread.sleep(1000);
        } catch(Exception e) {
        }
    }

    @Test
    void should_retrieve_inserted_vector_by_ann() {
        String sourceSentence         = "Testing is doubting !";
        Embedding sourceEmbedding     = embeddingModel().embed(sourceSentence).content();
        TextSegment sourceTextSegment = TextSegment.from(sourceSentence);
        String id =  embeddingStore().add(sourceEmbedding, sourceTextSegment);
        assertThat(id != null && !id.isEmpty()).isTrue();

        List<EmbeddingMatch<TextSegment>> embeddingMatches = embeddingStore.findRelevant(sourceEmbedding, 10);
        assertThat(embeddingMatches).hasSize(1);

        EmbeddingMatch<TextSegment> embeddingMatch = embeddingMatches.get(0);
        assertThat(embeddingMatch.score()).isBetween(0d, 1d);
        assertThat(embeddingMatch.embeddingId()).isEqualTo(id);
        assertThat(embeddingMatch.embedding()).isEqualTo(sourceEmbedding);
        assertThat(embeddingMatch.embedded()).isEqualTo(sourceTextSegment);
    }

    @Test
    void should_retrieve_inserted_vector_by_ann_and_metadata() {
        String sourceSentence         = "In GOD we trust, everything else we test!";
        Embedding sourceEmbedding     = embeddingModel().embed(sourceSentence).content();
        TextSegment sourceTextSegment = TextSegment.from(sourceSentence, new Metadata()
                .put("user", "GOD")
                .put("test", "false"));
        String id =  embeddingStore().add(sourceEmbedding, sourceTextSegment);
        assertThat(id != null && !id.isEmpty()).isTrue();

        // Should be found with no filter
        List<EmbeddingMatch<TextSegment>> matchesAnnOnly = embeddingStore
                .findRelevant(sourceEmbedding, 10);
        assertThat(matchesAnnOnly).hasSize(1);

        // Should retrieve if user is god
        List<EmbeddingMatch<TextSegment>> matchesGod = embeddingStore
                .findRelevant(sourceEmbedding, 10, .5d, Metadata.from("user", "GOD"));
        assertThat(matchesGod).hasSize(1);

        List<EmbeddingMatch<TextSegment>> matchesJohn = embeddingStore
                .findRelevant(sourceEmbedding, 10, .5d, Metadata.from("user", "JOHN"));
        assertThat(matchesJohn).isEmpty();
    }

    // metrics returned are 1.95% off we updated to "withPercentage(2)"

    @Test
    void should_return_correct_score() {
        Embedding embedding = embeddingModel().embed("hello").content();
        String id = embeddingStore().add(embedding);
        assertThat(id).isNotBlank();
        Embedding referenceEmbedding = embeddingModel().embed("hi").content();
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(referenceEmbedding, 1);
        assertThat(relevant).hasSize(1);
        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(
                RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(embedding, referenceEmbedding)),
                withPercentage(2)
        );
    }

    @Test
    void should_find_with_min_score() {
        String firstId = randomUUID();
        Embedding firstEmbedding = embeddingModel().embed("hello").content();
        embeddingStore().add(firstId, firstEmbedding);
        String secondId = randomUUID();
        Embedding secondEmbedding = embeddingModel().embed("hi").content();
        embeddingStore().add(secondId, secondEmbedding);
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(firstEmbedding, 10);
        assertThat(relevant).hasSize(2);
        EmbeddingMatch<TextSegment> firstMatch = relevant.get(0);
        assertThat(firstMatch.score()).isCloseTo(1, withPercentage(1));
        assertThat(firstMatch.embeddingId()).isEqualTo(firstId);
        EmbeddingMatch<TextSegment> secondMatch = relevant.get(1);
        assertThat(secondMatch.score()).isCloseTo(
                RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(firstEmbedding, secondEmbedding)),
                withPercentage(2)
        );
        assertThat(secondMatch.embeddingId()).isEqualTo(secondId);

        List<EmbeddingMatch<TextSegment>> relevant2 = embeddingStore().findRelevant(
                firstEmbedding,
                10,
                secondMatch.score() - 0.01
        );
        assertThat(relevant2).hasSize(2);
        assertThat(relevant2.get(0).embeddingId()).isEqualTo(firstId);
        assertThat(relevant2.get(1).embeddingId()).isEqualTo(secondId);

        List<EmbeddingMatch<TextSegment>> relevant3 = embeddingStore().findRelevant(
                firstEmbedding,
                10,
                secondMatch.score()
        );
        assertThat(relevant3).hasSize(2);
        assertThat(relevant3.get(0).embeddingId()).isEqualTo(firstId);
        assertThat(relevant3.get(1).embeddingId()).isEqualTo(secondId);

        List<EmbeddingMatch<TextSegment>> relevant4 = embeddingStore().findRelevant(
                firstEmbedding,
                10,
                secondMatch.score() + 0.01
        );
        assertThat(relevant4).hasSize(1);
        assertThat(relevant4.get(0).embeddingId()).isEqualTo(firstId);
    }

    @Test
    void should_add_multiple_embeddings_with_segments() {

        TextSegment firstSegment = TextSegment.from("hello");
        Embedding firstEmbedding = embeddingModel().embed(firstSegment.text()).content();

        TextSegment secondSegment = TextSegment.from("hi");
        Embedding secondEmbedding = embeddingModel().embed(secondSegment.text()).content();

        List<String> ids = embeddingStore().addAll(
                asList(firstEmbedding, secondEmbedding),
                asList(firstSegment, secondSegment)
        );
        assertThat(ids).hasSize(2);
        assertThat(ids.get(0)).isNotBlank();
        assertThat(ids.get(1)).isNotBlank();
        assertThat(ids.get(0)).isNotEqualTo(ids.get(1));

        awaitUntilPersisted();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(firstEmbedding, 10);
        assertThat(relevant).hasSize(2);

        EmbeddingMatch<TextSegment> firstMatch = relevant.get(0);
        assertThat(firstMatch.score()).isCloseTo(1, withPercentage(1));
        assertThat(firstMatch.embeddingId()).isEqualTo(ids.get(0));
        assertThat(firstMatch.embedding()).isEqualTo(firstEmbedding);
        assertThat(firstMatch.embedded()).isEqualTo(firstSegment);

        EmbeddingMatch<TextSegment> secondMatch = relevant.get(1);
        assertThat(secondMatch.score()).isCloseTo(
                RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(firstEmbedding, secondEmbedding)),
                withPercentage(2)
        );
        assertThat(secondMatch.embeddingId()).isEqualTo(ids.get(1));
        assertThat(secondMatch.embedding()).isEqualTo(secondEmbedding);
        assertThat(secondMatch.embedded()).isEqualTo(secondSegment);
    }



    @Test
    void should_add_multiple_embeddings() {

        Embedding firstEmbedding = embeddingModel().embed("hello").content();
        Embedding secondEmbedding = embeddingModel().embed("hi").content();

        List<String> ids = embeddingStore().addAll(asList(firstEmbedding, secondEmbedding));
        assertThat(ids).hasSize(2);
        assertThat(ids.get(0)).isNotBlank();
        assertThat(ids.get(1)).isNotBlank();
        assertThat(ids.get(0)).isNotEqualTo(ids.get(1));

        awaitUntilPersisted();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore().findRelevant(firstEmbedding, 10);
        assertThat(relevant).hasSize(2);

        EmbeddingMatch<TextSegment> firstMatch = relevant.get(0);
        assertThat(firstMatch.score()).isCloseTo(1, withPercentage(2));
        assertThat(firstMatch.embeddingId()).isEqualTo(ids.get(0));
        assertThat(firstMatch.embedding()).isEqualTo(firstEmbedding);
        assertThat(firstMatch.embedded()).isNull();

        EmbeddingMatch<TextSegment> secondMatch = relevant.get(1);
        assertThat(secondMatch.score()).isCloseTo(
                RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(firstEmbedding, secondEmbedding)),
                withPercentage(2)
        );
        assertThat(secondMatch.embeddingId()).isEqualTo(ids.get(1));
        assertThat(secondMatch.embedding()).isEqualTo(secondEmbedding);
        assertThat(secondMatch.embedded()).isNull();
    }


}
