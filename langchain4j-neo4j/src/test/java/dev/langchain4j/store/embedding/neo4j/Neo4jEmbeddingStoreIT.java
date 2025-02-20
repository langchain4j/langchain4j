package dev.langchain4j.store.embedding.neo4j;

import static dev.langchain4j.Neo4jTestUtils.getNeo4jContainer;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_EMBEDDING_PROP;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_ID_PROP;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_TEXT_PROP;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.data.Percentage.withPercentage;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.neo4j.cypherdsl.support.schema_name.SchemaNames;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.types.Node;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class Neo4jEmbeddingStoreIT {

    public static final String USERNAME = "neo4j";
    public static final String ADMIN_PASSWORD = "adminPass";
    public static final String LABEL_TO_SANITIZE = "Label ` to \\ sanitize";

    @Container
    static Neo4jContainer<?> neo4jContainer = getNeo4jContainer().withAdminPassword(ADMIN_PASSWORD);

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
                .label(LABEL_TO_SANITIZE)
                .build();
    }

    @AfterEach
    void afterEach() {
        session.run("MATCH (n) DETACH DELETE n");
        String indexName = ((Neo4jEmbeddingStore) embeddingStore).getIndexName();
        session.run("DROP INDEX " + SchemaNames.sanitize(indexName).get());
    }

    @Test
    void should_add_embedding() {
        Embedding embedding = embeddingModel.embed("embedText").content();

        String id = embeddingStore.add(embedding);
        assertThat(id).isNotNull();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isNull();

        checkEntitiesCreated(relevant.size(), iterator -> checkDefaultProps(embedding, match, iterator.next()));
    }

    @Test
    void should_add_embedding_with_id() {

        String id = randomUUID();
        Embedding embedding = embeddingModel.embed("embedText").content();

        embeddingStore.add(id, embedding);

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);
        assertThat(match.embedded()).isNull();

        checkEntitiesCreated(relevant.size(), iterator -> checkDefaultProps(embedding, match, iterator.next()));
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

        checkEntitiesCreated(relevant.size(), iterator -> {
            List<String> otherProps = Collections.singletonList(DEFAULT_TEXT_PROP);
            checkDefaultProps(embedding, match, iterator.next(), otherProps);
        });
    }

    @Test
    void should_add_embedding_with_segment_with_metadata() {
        checkSegmentWithMetadata(METADATA_KEY, LABEL_TO_SANITIZE);
    }

    @Test
    void should_add_embedding_with_segment_with_custom_metadata_prefix() {
        String metadataPrefix = "metadata.";
        String labelName = "CustomLabelName";
        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .metadataPrefix(metadataPrefix)
                .label(labelName)
                .indexName("customIdxName")
                .build();

        String metadataCompleteKey = metadataPrefix + METADATA_KEY;

        checkSegmentWithMetadata(metadataCompleteKey, labelName);
    }

    @Test
    void should_retrieve_custom_metadata_with_match() {
        String metadataPrefix = "metadata.";
        String labelName = "CustomLabelName";
        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .metadataPrefix(metadataPrefix)
                .label(labelName)
                .indexName("customIdxName")
                .retrievalQuery(
                        "RETURN {foo: 'bar'} AS metadata, node.text AS text, node.embedding AS embedding, node.id AS id, score")
                .build();

        String text = randomUUID();
        TextSegment segment = TextSegment.from(text, Metadata.from(METADATA_KEY, "test-value"));
        Embedding embedding = embeddingModel.embed(segment.text()).content();

        String id = embeddingStore.add(embedding, segment);
        assertThat(id).isNotNull();

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(embedding, 10);
        assertThat(relevant).hasSize(1);

        EmbeddingMatch<TextSegment> match = relevant.get(0);
        assertThat(match.score()).isCloseTo(1, withPercentage(1));
        assertThat(match.embeddingId()).isEqualTo(id);
        assertThat(match.embedding()).isEqualTo(embedding);

        TextSegment customMeta = TextSegment.from(text, Metadata.from("foo", "bar"));
        assertThat(match.embedded()).isEqualTo(customMeta);

        checkEntitiesCreated(relevant.size(), labelName, iterator -> {
            List<String> otherProps = Arrays.asList(DEFAULT_TEXT_PROP, metadataPrefix + METADATA_KEY);
            checkDefaultProps(embedding, DEFAULT_ID_PROP, match, iterator.next(), otherProps);
        });
    }

    @Test
    void should_add_embedding_with_segment_with_metadata_and_custom_id_prop() {
        String metadataPrefix = "metadata.";
        String customIdProp = "customId ` & Prop ` To Sanitize";

        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .metadataPrefix(metadataPrefix)
                .label("CustomLabelName")
                .indexName("customIdxName")
                .idProperty(customIdProp)
                .build();

        String metadataCompleteKey = metadataPrefix + METADATA_KEY;

        checkSegmentWithMetadata(metadataCompleteKey, customIdProp, "CustomLabelName");
    }

    @Test
    void should_add_multiple_embeddings() {
        Embedding firstEmbedding = embeddingModel.embed("firstEmbedText").content();
        Embedding secondEmbedding = embeddingModel.embed("secondEmbedText").content();

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

        checkEntitiesCreated(relevant.size(), iterator -> {
            iterator.forEachRemaining(node -> {
                if (node.get(DEFAULT_ID_PROP).asString().equals(firstMatch.embeddingId())) {
                    checkDefaultProps(firstEmbedding, firstMatch, node);
                } else {
                    checkDefaultProps(secondEmbedding, secondMatch, node);
                }
            });
        });
    }

    @Test
    void should_add_multiple_embeddings_with_segments() {

        TextSegment firstSegment = TextSegment.from("firstText");
        Embedding firstEmbedding = embeddingModel.embed(firstSegment.text()).content();
        TextSegment secondSegment = TextSegment.from("secondText");
        Embedding secondEmbedding = embeddingModel.embed(secondSegment.text()).content();

        List<String> ids =
                embeddingStore.addAll(asList(firstEmbedding, secondEmbedding), asList(firstSegment, secondSegment));
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

        checkEntitiesCreated(relevant.size(), iterator -> {
            List<String> otherProps = Collections.singletonList(DEFAULT_TEXT_PROP);
            iterator.forEachRemaining(node -> {
                if (node.get(DEFAULT_ID_PROP).asString().equals(firstMatch.embeddingId())) {
                    checkDefaultProps(firstEmbedding, firstMatch, node, otherProps);
                } else {
                    checkDefaultProps(secondEmbedding, secondMatch, node, otherProps);
                }
            });
        });
    }

    @Test
    void should_find_with_min_score() {

        String firstId = randomUUID();
        Embedding firstEmbedding = embeddingModel.embed("firstEmbedText").content();
        embeddingStore.add(firstId, firstEmbedding);

        String secondId = randomUUID();
        Embedding secondEmbedding = embeddingModel.embed("secondEmbedText").content();
        embeddingStore.add(secondId, secondEmbedding);

        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(firstEmbedding, 10);
        assertThat(relevant).hasSize(2);
        EmbeddingMatch<TextSegment> firstMatch = relevant.get(0);
        assertThat(firstMatch.score()).isCloseTo(1, withPercentage(1));
        assertThat(firstMatch.embeddingId()).isEqualTo(firstId);
        EmbeddingMatch<TextSegment> secondMatch = relevant.get(1);
        assertThat(secondMatch.score()).isBetween(0d, 1d);
        assertThat(secondMatch.embeddingId()).isEqualTo(secondId);

        List<EmbeddingMatch<TextSegment>> relevant2 =
                embeddingStore.findRelevant(firstEmbedding, 10, secondMatch.score() - 0.01);
        assertThat(relevant2).hasSize(2);
        assertThat(relevant2.get(0).embeddingId()).isEqualTo(firstId);
        assertThat(relevant2.get(1).embeddingId()).isEqualTo(secondId);

        List<EmbeddingMatch<TextSegment>> relevant3 =
                embeddingStore.findRelevant(firstEmbedding, 10, secondMatch.score());
        assertThat(relevant3).hasSize(2);
        assertThat(relevant3.get(0).embeddingId()).isEqualTo(firstId);
        assertThat(relevant3.get(1).embeddingId()).isEqualTo(secondId);

        List<EmbeddingMatch<TextSegment>> relevant4 =
                embeddingStore.findRelevant(firstEmbedding, 10, secondMatch.score() + 0.01);
        assertThat(relevant4).hasSize(1);
        assertThat(relevant4.get(0).embeddingId()).isEqualTo(firstId);

        checkEntitiesCreated(relevant.size(), iterator -> {
            iterator.forEachRemaining(node -> {
                if (node.get(DEFAULT_ID_PROP).asString().equals(firstMatch.embeddingId())) {
                    checkDefaultProps(firstEmbedding, firstMatch, node);
                } else {
                    checkDefaultProps(secondEmbedding, secondMatch, node);
                }
            });
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
        assertThat(match.score())
                .isCloseTo(
                        RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(embedding, referenceEmbedding)),
                        withPercentage(1));

        checkEntitiesCreated(relevant.size(), iterator -> checkDefaultProps(embedding, match, iterator.next()));
    }

    @Test
    void should_throw_error_if_another_index_name_with_different_label_exists() {
        String metadataPrefix = "metadata.";
        String idxName = "WillFail";

        embeddingStore = Neo4jEmbeddingStore.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .dimension(384)
                .indexName(idxName)
                .metadataPrefix(metadataPrefix)
                .awaitIndexTimeout(20)
                .build();

        String secondLabel = "Second label";
        try {
            embeddingStore = Neo4jEmbeddingStore.builder()
                    .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                    .dimension(384)
                    .label(secondLabel)
                    .indexName(idxName)
                    .metadataPrefix(metadataPrefix)
                    .build();
            fail("Should fail due to idx conflict");
        } catch (RuntimeException e) {
            String errMsg = String.format(
                    "It's not possible to create an index for the label `%s` and the property `%s`",
                    secondLabel, DEFAULT_EMBEDDING_PROP);
            assertThat(e.getMessage()).contains(errMsg);
        }
    }

    @Test
    void row_batches_single_element() {
        List<List<Map<String, Object>>> rowsBatched = getListRowsBatched(1);
        assertThat(rowsBatched).hasSize(1);
        assertThat(rowsBatched.get(0)).hasSize(1);
    }

    @Test
    void row_batches_10000_elements() {
        List<List<Map<String, Object>>> rowsBatched = getListRowsBatched(10000);
        assertThat(rowsBatched).hasSize(1);
        assertThat(rowsBatched.get(0)).hasSize(10000);
    }

    @Test
    void row_batches_20000_elements() {
        List<List<Map<String, Object>>> rowsBatched = getListRowsBatched(20000);
        assertThat(rowsBatched).hasSize(2);
        assertThat(rowsBatched.get(0)).hasSize(10000);
        assertThat(rowsBatched.get(1)).hasSize(10000);
    }

    @Test
    void row_batches_11001_elements() {
        List<List<Map<String, Object>>> rowsBatched = getListRowsBatched(11001);
        assertThat(rowsBatched).hasSize(2);
        assertThat(rowsBatched.get(0)).hasSize(10000);
        assertThat(rowsBatched.get(1)).hasSize(1001);
    }

    private List<List<Map<String, Object>>> getListRowsBatched(int numElements) {
        List<TextSegment> embedded = IntStream.range(0, numElements)
                .mapToObj(i -> TextSegment.from("text-" + i))
                .toList();
        List<String> ids =
                IntStream.range(0, numElements).mapToObj(i -> "id-" + i).toList();
        List<Embedding> embeddings = embeddingModel.embedAll(embedded).content();

        return Neo4jEmbeddingUtils.getRowsBatched((Neo4jEmbeddingStore) embeddingStore, ids, embeddings, embedded)
                .toList();
    }

    private void checkSegmentWithMetadata(String metadataKey, String labelName) {
        checkSegmentWithMetadata(metadataKey, DEFAULT_ID_PROP, labelName);
    }

    private void checkSegmentWithMetadata(String metadataKey, String idProp, String labelName) {
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

        checkEntitiesCreated(relevant.size(), labelName, iterator -> {
            List<String> otherProps = Arrays.asList(DEFAULT_TEXT_PROP, metadataKey);
            checkDefaultProps(embedding, idProp, match, iterator.next(), otherProps);
        });
    }

    private void checkEntitiesCreated(int expectedSize, Consumer<Iterator<Node>> nodeConsumer) {
        checkEntitiesCreated(expectedSize, LABEL_TO_SANITIZE, nodeConsumer);
    }

    private void checkEntitiesCreated(int expectedSize, String labelName, Consumer<Iterator<Node>> nodeConsumer) {
        String query = "MATCH (n:%s) RETURN n ORDER BY n.%s"
                .formatted(SchemaNames.sanitize(labelName).get(), DEFAULT_TEXT_PROP);

        List<Node> n = session.run(query).list(i -> i.get("n").asNode());

        assertThat(n).hasSize(expectedSize);

        Iterator<Node> iterator = n.iterator();
        nodeConsumer.accept(iterator);

        assertThat(iterator).isExhausted();
    }

    private void checkDefaultProps(Embedding embedding, EmbeddingMatch<TextSegment> match, Node node) {
        checkDefaultProps(embedding, DEFAULT_ID_PROP, match, node, Collections.emptyList());
    }

    private void checkDefaultProps(
            Embedding embedding, EmbeddingMatch<TextSegment> match, Node node, List<String> otherProps) {
        checkDefaultProps(embedding, DEFAULT_ID_PROP, match, node, otherProps);
    }

    private void checkDefaultProps(
            Embedding embedding, String idProp, EmbeddingMatch<TextSegment> match, Node node, List<String> otherProps) {
        checkPropKeys(node, idProp, otherProps);

        assertThat(node.get(idProp).asString()).isEqualTo(match.embeddingId());

        List<Float> floats = node.get(DEFAULT_EMBEDDING_PROP).asList(Value::asFloat);
        assertThat(floats).isEqualTo(embedding.vectorAsList());
    }

    private void checkPropKeys(Node node, String idProp, List<String> otherProps) {
        List<String> strings = new ArrayList<>();
        // default props
        strings.add(idProp);
        strings.add(DEFAULT_EMBEDDING_PROP);
        // other props
        strings.addAll(otherProps);

        assertThat(node.keys()).containsExactlyInAnyOrderElementsOf(strings);
    }
}
