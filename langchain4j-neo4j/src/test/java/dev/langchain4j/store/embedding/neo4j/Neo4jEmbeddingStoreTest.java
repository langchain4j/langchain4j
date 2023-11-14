package dev.langchain4j.store.embedding.neo4j;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_EMBEDDING_PROP;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_LABEL;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_TEXT_PROP;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.ID_ROW_KEY;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Percentage.withPercentage;

@Testcontainers
class Neo4jEmbeddingStoreTest {

    public static final String USERNAME = "neo4j";
    public static final String ADMIN_PASSWORD = "adminPass";
    @Container
    static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>(DockerImageName.parse("neo4j:5.13"))
            .withAdminPassword(ADMIN_PASSWORD);

    private static final String METADATA_KEY = "test-key";

    private EmbeddingStore<TextSegment> embeddingStore;

    private final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    private static Session session; 


    @BeforeAll
    static void startContainer() {
        neo4jContainer.start();
        Driver driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(), AuthTokens.basic(USERNAME, ADMIN_PASSWORD));
        session = driver.session();
    }

    @BeforeEach
    void initEmptyNeo4jEmbeddingStore() {
        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .build();
    }

    @AfterEach
    void afterEach() {
        session.run("MATCH (n) DETACH DELETE n");
    }

    @Test
    void should_add_embedding() {
        Embedding embedding = embeddingModel.embed(randomUUID()).content();

        String id = embeddingStore.add(embedding);
        assertThat(id).isNotNull();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isNull();

        checkEntitiesCreated(relevant.size(), 
                iterator -> checkDefaultProps(embedding, match, iterator.next()));
    }

    @Test
    void should_add_embedding_with_id() {

        String id = randomUUID();
        Embedding embedding = embeddingModel.embed(randomUUID()).content();

        embeddingStore.add(id, embedding);

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isNull();


        checkEntitiesCreated(relevant.size(), 
                iterator -> checkDefaultProps(embedding, match, iterator.next()));
    }

    @Test
    void should_add_embedding_with_segment() {

        TextSegment segment = TextSegment.from(randomUUID());
        Embedding embedding = embeddingModel.embed(segment.text()).content();

        String id = embeddingStore.add(embedding, segment);
        assertThat(id).isNotNull();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isEqualTo(segment);


        checkEntitiesCreated(relevant.size(), 
                iterator -> {
            List<String> otherProps = List.of(DEFAULT_TEXT_PROP);
            checkDefaultProps(embedding, match, iterator.next(), otherProps);
        });
    }

    @Test
    void should_add_embedding_with_segment_with_metadata() {
        checkSegmentWithMetadata(METADATA_KEY);
    }

    @Test
    void should_add_embedding_with_segment_with_custom_metadata_prefix() {
        String metadataPrefix = "metadata.";
        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .metadataPrefix(metadataPrefix)
                .build();
        
        String metadataCompleteKey = metadataPrefix + METADATA_KEY;

        checkSegmentWithMetadata(metadataCompleteKey);
    }


    @Test
    void should_add_multiple_embeddings() {

        Embedding firstEmbedding = embeddingModel.embed(randomUUID()).content();
        Embedding secondEmbedding = embeddingModel.embed(randomUUID()).content();

        List<String> ids = embeddingStore.addAll(asList(firstEmbedding, secondEmbedding));
        assertThat(ids).hasSize(2);

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

        checkEntitiesCreated(relevant.size(), 
                iterator -> {
            checkDefaultProps(firstEmbedding, firstMatch, iterator.next());
            checkDefaultProps(secondEmbedding, secondMatch, iterator.next());
        });
    }

    @Test
    void should_add_multiple_embeddings_with_segments() {

        TextSegment firstSegment = TextSegment.from(randomUUID());
        Embedding firstEmbedding = embeddingModel.embed(firstSegment.text()).content();
        TextSegment secondSegment = TextSegment.from(randomUUID());
        Embedding secondEmbedding = embeddingModel.embed(secondSegment.text()).content();

        List<String> ids = embeddingStore.addAll(
                asList(firstEmbedding, secondEmbedding),
                asList(firstSegment, secondSegment)
        );
        assertThat(ids).hasSize(2);

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

        checkEntitiesCreated(relevant.size(), 
                iterator -> {
            List<String> otherProps = List.of(DEFAULT_TEXT_PROP);
            checkDefaultProps(firstEmbedding, firstMatch, iterator.next(), otherProps);
            checkDefaultProps(secondEmbedding, secondMatch, iterator.next(), otherProps);
        });
    }

    @Test
    void should_find_with_min_score() {

        String firstId = randomUUID();
        Embedding firstEmbedding = embeddingModel.embed(randomUUID()).content();
        embeddingStore.add(firstId, firstEmbedding);

        String secondId = randomUUID();
        Embedding secondEmbedding = embeddingModel.embed(randomUUID()).content();
        embeddingStore.add(secondId, secondEmbedding);

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

        checkEntitiesCreated(relevant.size(),
                iterator -> {
            checkDefaultProps(firstEmbedding, firstMatch, iterator.next());
            checkDefaultProps(secondEmbedding, secondMatch, iterator.next());
        });
    }

    @Test
    void should_return_correct_score() {

        Embedding embedding = embeddingModel.embed("hello").content();

        String id = embeddingStore.add(embedding);
        assertThat(id).isNotNull();

        Embedding referenceEmbedding = embeddingModel.embed("hi").content();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(referenceEmbedding, 1);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(
                RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(embedding, referenceEmbedding)),
                withPercentage(1)
        );
        
        checkEntitiesCreated(relevant.size(),
                iterator -> checkDefaultProps(embedding, match, iterator.next()));
    }

    private void checkSegmentWithMetadata(String metadataKey) {
        TextSegment segment = TextSegment.from(randomUUID(), Metadata.from(METADATA_KEY, "test-value"));
        Embedding embedding = embeddingModel.embed(segment.text()).content();

        String id = embeddingStore.add(embedding, segment);
        assertThat(id).isNotNull();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isEqualTo(segment);

        checkEntitiesCreated(relevant.size(),
                iterator -> {
                    List<String> otherProps = List.of(DEFAULT_TEXT_PROP, metadataKey);
                    checkDefaultProps(embedding, match, iterator.next(), otherProps);
                });
    }

    private void checkEntitiesCreated(int expectedSize, Consumer<Iterator<Node>> nodeConsumer) {
        String query = "MATCH (n:%s) RETURN n".formatted(DEFAULT_LABEL);
        
        List<Node> n = session.run(query)
                .list(i -> i.get("n").asNode());

        assertThat(n).hasSize(expectedSize);

        Iterator<Node> iterator = n.iterator();
        nodeConsumer.accept(iterator);

        assertThat(iterator).isExhausted();
    }

    private void checkDefaultProps(Embedding embedding, EmbeddingMatch<TextSegment> match, Node node) {
        checkDefaultProps(embedding, match, node, List.of());
    }

    private void checkDefaultProps(Embedding embedding, EmbeddingMatch<TextSegment> match, Node node, List<String> otherProps) {
        checkPropKeys(node, otherProps);

        assertThat(node.get(ID_ROW_KEY).asString()).isEqualTo(match.embeddingId());

        List<Float> floats = node.get(DEFAULT_EMBEDDING_PROP).asList(Value::asFloat);
        assertThat(floats).isEqualTo(embedding.vectorAsList());
    }

    private void checkPropKeys(Node node, List<String> otherProps) {
        List<String> strings = new ArrayList<>();
        // default props
        strings.add(ID_ROW_KEY);
        strings.add(DEFAULT_EMBEDDING_PROP);
        // other props
        strings.addAll(otherProps);

        assertThat(node.keys())
                .containsExactlyInAnyOrderElementsOf(strings);
    }
}
