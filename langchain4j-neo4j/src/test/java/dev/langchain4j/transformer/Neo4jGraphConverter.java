package dev.langchain4j.transformer;

import static dev.langchain4j.Neo4jTestUtils.getNeo4jContainer;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.store.graph.neo4j.BaseNeo4jBuilder.DEFAULT_ID_PROP;
import static dev.langchain4j.store.graph.neo4j.BaseNeo4jBuilder.DEFAULT_TEXT_PROP;
import static dev.langchain4j.store.graph.neo4j.Neo4jGraph.DEFAULT_ENTITY_LABEL;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.DefaultDocument;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.graph.neo4j.Neo4jGraph;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.neo4j.cypherdsl.support.schema_name.SchemaNames;
import org.neo4j.driver.Record;
import org.neo4j.driver.internal.util.Iterables;
import org.neo4j.driver.internal.value.PathValue;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.neo4j.driver.types.Relationship;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class Neo4jGraphConverter {
    public static final String ON = "on";
    public static final String KEY_CAT = "key2";
    public static final String VALUE_CAT = "value2";
    public static final String KEY_KEANU = "key33";
    public static final String VALUE_KEANU = "value3";
    public static String USERNAME = "neo4j";
    public static String ADMIN_PASSWORD = "adminPass";

    public static String CAT_ON_THE_TABLE = "Sylvester the cat is on the table";
    public static String KEANU_REEVES_ACTED = "Keanu Reeves acted in Matrix";
    public static String MATCH_P_RETURN_P = "MATCH p=(n)-[]->() RETURN p ORDER BY n.%s";
    public static String MATCH_P_DOCUMENT_MENTIONS_RETURN_P =
            "MATCH p=(:Document)-[:MENTIONS]->(n)-[]->() RETURN p ORDER BY n.%s";
    public static String KEANU = "keanu";
    public static String MATRIX = "matrix";
    public static String ACTED = "acted";
    public static String SYLVESTER = "sylvester";
    public static String TABLE = "table";
    private static LLMGraphTransformer graphTransformer;
    private static List<GraphDocument> graphDocs;
    private static Neo4jGraph graph;

    public static String CUSTOM_TEXT = "custom  `text";
    public static String CUSTOM_ID = "custom  ` id";
    public static final String SANITIZED_CUSTOM_ID =
            SchemaNames.sanitize(CUSTOM_ID).get();
    public static String CUSTOM_LABEL = "Label ` to \\ sanitize";

    @Container
    static Neo4jContainer<?> neo4jContainer =
            getNeo4jContainer().withAdminPassword(ADMIN_PASSWORD).withPlugins("apoc");

    @BeforeAll
    static void beforeEach() {

        ChatLanguageModel model = OpenAiChatModel.builder()
                .baseUrl("http://langchain4j.dev/demo/openai/v1")
                .apiKey("demo")
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .logRequests(true)
                .logResponses(true)
                .build();

        graphTransformer = LLMGraphTransformer.builder().model(model).build();
        graph = Neo4jGraph.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .build();

        // given
        Document docKeanu = new DefaultDocument(KEANU_REEVES_ACTED, Metadata.from(KEY_KEANU, VALUE_KEANU));
        Document docCat = new DefaultDocument(CAT_ON_THE_TABLE, Metadata.from(KEY_CAT, VALUE_CAT));
        List<Document> documents = List.of(docCat, docKeanu);
        // TODO - retry util??
        graphDocs = graphTransformer.convertToGraphDocuments(documents);
        assertThat(graphDocs.size()).isEqualTo(2);
    }

    @AfterEach
    void afterEach() {
        graph.executeWrite("MATCH (n) DETACH DELETE n");
    }

    @AfterAll
    static void afterAll() {
        graph.close();
    }

    @Test
    void testAddGraphDocuments() {
        addGraphDocumentsCommon();

        // retry to check that merge works correctly
        addGraphDocumentsCommon();
    }

    private static void addGraphDocumentsCommon() {
        // when
        graph.addGraphDocuments(graphDocs, false, false);

        // then
        List<Record> records = graph.executeRead(MATCH_P_RETURN_P.formatted(DEFAULT_ID_PROP));

        assertThat(records).hasSize(2);
        Record record = records.get(0);
        PathValue p = (PathValue) record.get("p");
        Path path = p.asPath();
        Node start = path.start();
        assertNodeWithoutBaseEntityLabel(start);
        assertNodeProps(start, KEANU, DEFAULT_ID_PROP);
        Node end = path.end();
        assertNodeWithoutBaseEntityLabel(end);
        assertNodeProps(end, MATRIX, DEFAULT_ID_PROP);
        Relationship rel = Iterables.single(path.relationships());
        assertThat(rel.type()).containsIgnoringCase(ACTED);

        record = records.get(1);
        p = (PathValue) record.get("p");
        path = p.asPath();
        start = path.start();
        assertNodeWithoutBaseEntityLabel(start);
        assertNodeProps(start, SYLVESTER, DEFAULT_ID_PROP);
        end = path.end();
        assertNodeWithoutBaseEntityLabel(end);
        assertNodeProps(end, TABLE, DEFAULT_ID_PROP);
        rel = Iterables.single(path.relationships());
        assertThat(rel.type()).containsIgnoringCase(ON);
    }

    @Test
    void testAddGraphDocumentsWithCustomIdTextAndLabel() {
        Neo4jGraph neo4jGraph = Neo4jGraph.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .textProperty(CUSTOM_TEXT)
                .idProperty(CUSTOM_ID)
                .label(CUSTOM_LABEL)
                .build();

        addGraphDocumentsWithCustomIdTextAndLabelCommon(neo4jGraph);

        // retry to check that merge works correctly
        addGraphDocumentsWithCustomIdTextAndLabelCommon(neo4jGraph);

        neo4jGraph.executeWrite("MATCH (n) DETACH DELETE n");
        neo4jGraph.close();
    }

    private static void addGraphDocumentsWithCustomIdTextAndLabelCommon(Neo4jGraph neo4jGraph) {
        neo4jGraph.addGraphDocuments(graphDocs, false, false);

        List<Record> records = neo4jGraph.executeRead(MATCH_P_RETURN_P.formatted(SANITIZED_CUSTOM_ID));
        assertThat(records).hasSize(2);
        Record record = records.get(0);
        PathValue p = (PathValue) record.get("p");
        Path path = p.asPath();
        Node start = path.start();
        assertNodeWithoutBaseEntityLabel(start);
        assertNodeProps(start, KEANU, CUSTOM_ID);
        Node end = path.end();
        assertNodeWithoutBaseEntityLabel(end);
        assertNodeProps(end, MATRIX, CUSTOM_ID);
        Relationship rel = Iterables.single(path.relationships());
        assertThat(rel.type()).containsIgnoringCase(ACTED);

        record = records.get(1);
        p = (PathValue) record.get("p");
        path = p.asPath();
        start = path.start();
        assertNodeWithoutBaseEntityLabel(start);
        assertNodeProps(start, SYLVESTER, CUSTOM_ID);
        end = path.end();
        assertNodeWithoutBaseEntityLabel(end);
        assertNodeProps(end, TABLE, CUSTOM_ID);
        rel = Iterables.single(path.relationships());
        assertThat(rel.type()).containsIgnoringCase(ON);
    }

    @Test
    void testAddGraphDocumentsWithBaseEntityLabel() {

        addGraphDocumentsWithBaseEntityLabelCommon();

        // retry to check that merge works correctly
        addGraphDocumentsWithBaseEntityLabelCommon();
    }

    private static void addGraphDocumentsWithBaseEntityLabelCommon() {
        // when
        graph.addGraphDocuments(graphDocs, false, true);

        // then
        List<Record> records = graph.executeRead(MATCH_P_RETURN_P.formatted(DEFAULT_ID_PROP));
        assertThat(records).hasSize(2);
        Record record = records.get(0);
        PathValue p = (PathValue) record.get("p");
        Path path = p.asPath();
        Node start = path.start();
        assertNodeWithBaseEntityLabel(start, DEFAULT_ENTITY_LABEL);
        assertNodeProps(start, KEANU, DEFAULT_ID_PROP);
        Node end = path.end();
        assertNodeWithBaseEntityLabel(end, DEFAULT_ENTITY_LABEL);
        assertNodeProps(end, MATRIX, DEFAULT_ID_PROP);
        Relationship rel = Iterables.single(path.relationships());
        assertThat(rel.type()).containsIgnoringCase(ACTED);

        record = records.get(1);
        p = (PathValue) record.get("p");
        path = p.asPath();
        start = path.start();
        assertNodeWithBaseEntityLabel(start, DEFAULT_ENTITY_LABEL);
        assertNodeProps(start, SYLVESTER, DEFAULT_ID_PROP);
        end = path.end();
        assertNodeWithBaseEntityLabel(end, DEFAULT_ENTITY_LABEL);
        assertNodeProps(end, TABLE, DEFAULT_ID_PROP);
        rel = Iterables.single(path.relationships());
        assertThat(rel.type()).containsIgnoringCase(ON);
    }

    @Test
    void testAddGraphDocumentsWithBaseEntityLabelAndIncludeSource() {
        testWithBaseEntityLabelAndIncludeSourceCommon();

        // retry to check that merge works correctly
        testWithBaseEntityLabelAndIncludeSourceCommon();
    }

    private static void testWithBaseEntityLabelAndIncludeSourceCommon() {
        // when
        graph.addGraphDocuments(graphDocs, true, true);

        // then
        List<Record> records = graph.executeRead(MATCH_P_DOCUMENT_MENTIONS_RETURN_P.formatted(DEFAULT_ID_PROP));
        assertThat(records).hasSize(2);
        Record record = records.get(0);
        PathValue p = (PathValue) record.get("p");
        Path path = p.asPath();
        Iterator<Node> iterator = path.nodes().iterator();
        Node node = iterator.next();
        assertThat(node.labels()).hasSize(1);
        assertionsDocument(node, DEFAULT_ID_PROP, DEFAULT_TEXT_PROP, KEANU_REEVES_ACTED, KEY_KEANU, VALUE_KEANU);

        node = iterator.next();
        assertNodeWithBaseEntityLabel(node, DEFAULT_ENTITY_LABEL);
        assertNodeProps(node, KEANU, DEFAULT_ID_PROP);

        node = iterator.next();
        assertNodeWithBaseEntityLabel(node, DEFAULT_ENTITY_LABEL);
        assertNodeProps(node, MATRIX, DEFAULT_ID_PROP);
        List<Relationship> rels = Iterables.asList(path.relationships());
        assertThat(rels).hasSize(2);
        assertThat(rels.get(1).type()).containsIgnoringCase(ACTED);

        record = records.get(1);
        p = (PathValue) record.get("p");
        path = p.asPath();
        iterator = path.nodes().iterator();
        node = iterator.next();
        assertThat(node.labels()).hasSize(1);
        assertionsDocument(node, DEFAULT_ID_PROP, DEFAULT_TEXT_PROP, CAT_ON_THE_TABLE, KEY_CAT, VALUE_CAT);

        node = iterator.next();
        assertNodeWithBaseEntityLabel(node, DEFAULT_ENTITY_LABEL);
        assertNodeProps(node, SYLVESTER, DEFAULT_ID_PROP);

        node = iterator.next();
        assertNodeWithBaseEntityLabel(node, DEFAULT_ENTITY_LABEL);
        assertNodeProps(node, TABLE, DEFAULT_ID_PROP);
        rels = Iterables.asList(path.relationships());
        assertThat(rels).hasSize(2);
        assertThat(rels.get(1).type()).containsIgnoringCase(ON);
    }

    @Test
    void testAddGraphDocumentsWithBaseEntityLabelIncludeSourceAndCustomIdTextAndLabel() {

        Neo4jGraph neo4jGraph = Neo4jGraph.builder()
                .withBasicAuth(neo4jContainer.getBoltUrl(), USERNAME, ADMIN_PASSWORD)
                .textProperty(CUSTOM_TEXT)
                .idProperty(CUSTOM_ID)
                .label(CUSTOM_LABEL)
                .build();

        baseEntityLabelIncludeSourceAndCustomIdTextAndLabelCommon(neo4jGraph);

        // retry to check that merge works correctly
        baseEntityLabelIncludeSourceAndCustomIdTextAndLabelCommon(neo4jGraph);

        neo4jGraph.executeWrite("MATCH (n) DETACH DELETE n");
        neo4jGraph.close();
    }

    private static void baseEntityLabelIncludeSourceAndCustomIdTextAndLabelCommon(Neo4jGraph neo4jGraph) {
        neo4jGraph.addGraphDocuments(graphDocs, true, true);

        List<Record> records = graph.executeRead(MATCH_P_DOCUMENT_MENTIONS_RETURN_P.formatted(SANITIZED_CUSTOM_ID));
        assertThat(records).hasSize(2);
        Record record = records.get(0);
        PathValue p = (PathValue) record.get("p");
        Path path = p.asPath();
        Iterator<Node> iterator = path.nodes().iterator();
        Node node = iterator.next();
        assertThat(node.labels()).hasSize(1);
        assertionsDocument(node, CUSTOM_ID, CUSTOM_TEXT, KEANU_REEVES_ACTED, KEY_KEANU, VALUE_KEANU);

        node = iterator.next();
        assertNodeWithBaseEntityLabel(node, CUSTOM_LABEL);
        assertNodeProps(node, KEANU, CUSTOM_ID);

        node = iterator.next();
        assertNodeWithBaseEntityLabel(node, CUSTOM_LABEL);
        assertNodeProps(node, MATRIX, CUSTOM_ID);
        List<Relationship> rels = Iterables.asList(path.relationships());
        assertThat(rels).hasSize(2);
        assertThat(rels.get(1).type()).containsIgnoringCase(ACTED);

        record = records.get(1);
        p = (PathValue) record.get("p");
        path = p.asPath();
        iterator = path.nodes().iterator();
        node = iterator.next();
        assertThat(node.labels()).hasSize(1);
        assertionsDocument(node, CUSTOM_ID, CUSTOM_TEXT, CAT_ON_THE_TABLE, KEY_CAT, VALUE_CAT);

        node = iterator.next();
        assertNodeWithBaseEntityLabel(node, CUSTOM_LABEL);
        assertNodeProps(node, SYLVESTER, CUSTOM_ID);

        node = iterator.next();
        assertNodeWithBaseEntityLabel(node, CUSTOM_LABEL);
        assertNodeProps(node, TABLE, CUSTOM_ID);
        rels = Iterables.asList(path.relationships());
        assertThat(rels).hasSize(2);
        assertThat(rels.get(1).type()).containsIgnoringCase(ON);
    }

    @Test
    void testAddGraphDocumentsWithIncludeSource() {
        testAddGraphDocumentsWithIncludeSourceCommon();

        // retry to check that merge works correctly
        testAddGraphDocumentsWithIncludeSourceCommon();
    }

    private static void testAddGraphDocumentsWithIncludeSourceCommon() {
        // when
        graph.addGraphDocuments(graphDocs, true, false);

        // then
        List<Record> records = graph.executeRead(MATCH_P_DOCUMENT_MENTIONS_RETURN_P.formatted(DEFAULT_ID_PROP));
        assertThat(records).hasSize(2);
        Record record = records.get(0);

        Path path = record.get("p").asPath();
        Iterator<Node> iterator = path.nodes().iterator();
        Node node = iterator.next();
        assertThat(node.labels()).hasSize(1);
        assertionsDocument(node, DEFAULT_ID_PROP, DEFAULT_TEXT_PROP, KEANU_REEVES_ACTED, KEY_KEANU, VALUE_KEANU);

        node = iterator.next();
        assertNodeWithoutBaseEntityLabel(node);
        assertNodeProps(node, KEANU, DEFAULT_ID_PROP);

        node = iterator.next();
        assertNodeWithoutBaseEntityLabel(node);
        assertNodeProps(node, MATRIX, DEFAULT_ID_PROP);
        List<Relationship> rels = Iterables.asList(path.relationships());
        assertThat(rels).hasSize(2);
        assertThat(rels.get(1).type()).containsIgnoringCase(ACTED);

        record = records.get(1);

        path = record.get("p").asPath();
        iterator = path.nodes().iterator();
        node = iterator.next();
        assertThat(node.labels()).hasSize(1);
        assertionsDocument(node, DEFAULT_ID_PROP, DEFAULT_TEXT_PROP, CAT_ON_THE_TABLE, KEY_CAT, VALUE_CAT);

        node = iterator.next();
        assertNodeWithoutBaseEntityLabel(node);
        assertNodeProps(node, SYLVESTER, DEFAULT_ID_PROP);

        node = iterator.next();
        assertNodeWithoutBaseEntityLabel(node);
        assertNodeProps(node, TABLE, DEFAULT_ID_PROP);
        rels = Iterables.asList(path.relationships());
        assertThat(rels).hasSize(2);
        assertThat(rels.get(1).type()).containsIgnoringCase(ON);
    }

    private static void assertNodeWithoutBaseEntityLabel(Node start) {
        Iterable<String> labels = start.labels();
        assertThat(labels).hasSize(1);
        assertThat(labels).doesNotContain(DEFAULT_ENTITY_LABEL);
    }

    private static void assertNodeWithBaseEntityLabel(Node start, String entityLabel) {
        Iterable<String> labels = start.labels();
        assertThat(labels).hasSize(2);
        assertThat(labels).contains(entityLabel);
    }

    private static void assertNodeProps(Node start, String propRegex, String idProp) {
        Map<String, Object> map = start.asMap();
        assertThat(map).hasSize(1);
        assertThat((String) map.get(idProp)).containsIgnoringCase(propRegex);
    }

    private static void assertionsDocument(
            Node start,
            String idProp,
            String textProp,
            String expectedText,
            String expectedMetaKey,
            String expectedMetaValue) {
        Map<String, Object> map = start.asMap();
        assertThat(map.size()).isEqualTo(3);
        assertThat(map).containsKey(idProp);
        Object text = map.get(textProp);
        assertThat(text).isEqualTo(expectedText);

        final Object actualMetaValue = map.get(expectedMetaKey);
        assertThat(actualMetaValue).isEqualTo(expectedMetaValue);
    }
}
