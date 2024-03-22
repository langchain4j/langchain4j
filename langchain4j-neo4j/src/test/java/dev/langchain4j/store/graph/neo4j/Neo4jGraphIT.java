package dev.langchain4j.store.graph.neo4j;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.summary.ResultSummary;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Neo4jGraphIT {

    @Container
    private static final Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>(DockerImageName.parse("neo4j:5.16.0"))
            .withoutAuthentication()
            .withLabsPlugins("apoc");

    private static Neo4jGraph neo4jGraph;

    @BeforeAll
    static void startContainer() {

        neo4jContainer.start();
        Driver driver = GraphDatabase.driver(neo4jContainer.getBoltUrl(), AuthTokens.none());
        neo4jGraph = Neo4jGraph.builder().driver(driver).build();
    }

    @AfterAll
    static void stopContainer() {

        neo4jGraph.close();
        neo4jContainer.stop();
    }

    @Test
    @Order(1)
    void refreshSchemaShouldReturnEmptySchema() {

        neo4jGraph.refreshSchema();
        String expectedSchema = "Node properties are the following:\n\n\nRelationship properties are the following:\n\n\nThe relationships are the following:\n";
        assertEquals(expectedSchema, neo4jGraph.getSchema());
    }

    @Test
    @Order(2)
    void executeWriteShouldExecuteQuery() {

        String query = "CREATE (n:Person {name: 'John'})";
        ResultSummary resultSummary = neo4jGraph.executeWrite(query);

        assertEquals(1, resultSummary.counters().nodesCreated());
    }

    @Test
    @Order(3)
    void executeReadShouldReturnRecords() {

        String query = "MATCH (n:Person) RETURN n";
        List<Record> records = neo4jGraph.executeRead(query);

        assertEquals("John", records.get(0).get("n").asNode().get("name").asString());
    }

    @Test
    @Order(4)
    void refreshSchemaShouldReturnUpdatedSchema() {

        neo4jGraph.refreshSchema();
        String expectedSchema = """
                Node properties are the following:
                Person {name:STRING}
                                
                Relationship properties are the following:
                                
                                
                The relationships are the following:
                """;
        assertEquals(expectedSchema, neo4jGraph.getSchema());
    }
}