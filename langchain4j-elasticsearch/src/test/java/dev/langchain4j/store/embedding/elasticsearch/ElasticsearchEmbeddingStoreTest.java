package dev.langchain4j.store.embedding.elasticsearch;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.internal.Utils.randomUUID;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

@Disabled("needs Elasticsearch to be running locally")
class ElasticsearchEmbeddingStoreTest {

    /**
     * First start elasticsearch locally:
     * docker pull docker.elastic.co/elasticsearch/elasticsearch:8.9.0
     * docker run -d -p 9200:9200 -p 9300:9300 -e discovery.type=single-node -e xpack.security.enabled=false docker.elastic.co/elasticsearch/elasticsearch:8.9.0
     */

    private final EmbeddingStore<TextSegment> embeddingStore = ElasticsearchEmbeddingStore.builder()
            .serverUrl("http://localhost:9200")
            .indexName(randomUUID())
            .build();

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @Test
    void should_add_embedding() throws InterruptedException {

        Embedding embedding = embeddingModel.embed(randomUUID()).content();

        String id = embeddingStore.add(embedding);
        assertThat(id).isNotNull();

        Thread.sleep(2000);

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isNull();
    }

    @Test
    void should_add_embedding_with_id() throws InterruptedException {

        String id = randomUUID();
        Embedding embedding = embeddingModel.embed(randomUUID()).content();

        embeddingStore.add(id, embedding);

        Thread.sleep(2000);

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isNull();
    }

    @Test
    void should_add_embedding_with_segment() throws InterruptedException {

        TextSegment segment = TextSegment.from(randomUUID());
        Embedding embedding = embeddingModel.embed(segment.text()).content();

        String id = embeddingStore.add(embedding, segment);
        assertThat(id).isNotNull();

        Thread.sleep(2000);

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isEqualTo(segment);
    }

    @Test
    void should_add_embedding_with_segment_with_metadata() throws InterruptedException {

        TextSegment segment = TextSegment.from(randomUUID(), Metadata.from("test-key", "test-value"));
        Embedding embedding = embeddingModel.embed(segment.text()).content();

        String id = embeddingStore.add(embedding, segment);
        assertThat(id).isNotNull();

        Thread.sleep(2000);

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isEqualTo(segment);
    }

    @Test
    void should_add_multiple_embeddings() throws InterruptedException {

        Embedding firstEmbedding = embeddingModel.embed(randomUUID()).content();
        Embedding secondEmbedding = embeddingModel.embed(randomUUID()).content();

        List<String> ids = embeddingStore.addAll(asList(firstEmbedding, secondEmbedding));
        assertThat(ids).hasSize(2);

        Thread.sleep(2000);

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(firstEmbedding, 10);
        assertThat(relevant).hasSize(2);

        EmbeddingMatch<TextSegment> firstMatch = relevant.get(0);
        assertThat(firstMatch.score()).isCloseTo(1, withPercentage(1));
        assertThat(firstMatch.embeddingId()).isEqualTo(ids.get(0));
        assertThat(firstMatch.embedding()).isEqualTo(firstEmbedding);
        assertThat(firstMatch.embedded()).isNull();

        EmbeddingMatch<TextSegment> secondMatch = relevant.get(1);
        assertThat(secondMatch.score()).isBetween(0d, 1d);
        assertThat(secondMatch.embeddingId()).isEqualTo(ids.get(1));
        assertThat(secondMatch.embedding()).isEqualTo(secondEmbedding);
        assertThat(secondMatch.embedded()).isNull();
    }

    @Test
    void should_add_multiple_embeddings_with_segments() throws InterruptedException {

        TextSegment firstSegment = TextSegment.from(randomUUID());
        Embedding firstEmbedding = embeddingModel.embed(firstSegment.text()).content();
        TextSegment secondSegment = TextSegment.from(randomUUID());
        Embedding secondEmbedding = embeddingModel.embed(secondSegment.text()).content();

        List<String> ids = embeddingStore.addAll(
                asList(firstEmbedding, secondEmbedding),
                asList(firstSegment, secondSegment)
        );
        assertThat(ids).hasSize(2);

        Thread.sleep(2000);

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(firstEmbedding, 10);
        assertThat(relevant).hasSize(2);

        EmbeddingMatch<TextSegment> firstMatch = relevant.get(0);
        assertThat(firstMatch.score()).isCloseTo(1, withPercentage(1));
        assertThat(firstMatch.embeddingId()).isEqualTo(ids.get(0));
        assertThat(firstMatch.embedding()).isEqualTo(firstEmbedding);
        assertThat(firstMatch.embedded()).isEqualTo(firstSegment);

        EmbeddingMatch<TextSegment> secondMatch = relevant.get(1);
        assertThat(secondMatch.score()).isBetween(0d, 1d);
        assertThat(secondMatch.embeddingId()).isEqualTo(ids.get(1));
        assertThat(secondMatch.embedding()).isEqualTo(secondEmbedding);
        assertThat(secondMatch.embedded()).isEqualTo(secondSegment);
    }

    @Test
    void should_find_with_min_score() throws InterruptedException {

        String firstId = randomUUID();
        Embedding firstEmbedding = embeddingModel.embed(randomUUID()).content();
        embeddingStore.add(firstId, firstEmbedding);

        String secondId = randomUUID();
        Embedding secondEmbedding = embeddingModel.embed(randomUUID()).content();
        embeddingStore.add(secondId, secondEmbedding);

        Thread.sleep(2000);

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(firstEmbedding, 10);
        assertThat(relevant).hasSize(2);
        EmbeddingMatch<TextSegment> firstMatch = relevant.get(0);
        assertThat(firstMatch.score()).isCloseTo(1, withPercentage(1));
        assertThat(firstMatch.embeddingId()).isEqualTo(firstId);
        EmbeddingMatch<TextSegment> secondMatch = relevant.get(1);
        assertThat(secondMatch.score()).isBetween(0d, 1d);
        assertThat(secondMatch.embeddingId()).isEqualTo(secondId);

        List<EmbeddingMatch<TextSegment>> relevant2 = embeddingStore.findRelevant(
                firstEmbedding,
                10,
                secondMatch.score() - 0.01
        );
        assertThat(relevant2).hasSize(2);
        assertThat(relevant2.get(0).embeddingId()).isEqualTo(firstId);
        assertThat(relevant2.get(1).embeddingId()).isEqualTo(secondId);

        List<EmbeddingMatch<TextSegment>> relevant3 = embeddingStore.findRelevant(
                firstEmbedding,
                10,
                secondMatch.score()
        );
        assertThat(relevant3).hasSize(2);
        assertThat(relevant3.get(0).embeddingId()).isEqualTo(firstId);
        assertThat(relevant3.get(1).embeddingId()).isEqualTo(secondId);

        List<EmbeddingMatch<TextSegment>> relevant4 = embeddingStore.findRelevant(
                firstEmbedding,
                10,
                secondMatch.score() + 0.01
        );
        assertThat(relevant4).hasSize(1);
        assertThat(relevant4.get(0).embeddingId()).isEqualTo(firstId);
    }

    @Test
    void should_return_correct_score() throws InterruptedException {

        Embedding embedding = embeddingModel.embed("hello").content();

        String id = embeddingStore.add(embedding);
        assertThat(id).isNotNull();

        Embedding referenceEmbedding = embeddingModel.embed("hi").content();

        Thread.sleep(2000);

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(referenceEmbedding, 1);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(
                RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(embedding, referenceEmbedding)),
                withPercentage(1)
        );
    }
}
