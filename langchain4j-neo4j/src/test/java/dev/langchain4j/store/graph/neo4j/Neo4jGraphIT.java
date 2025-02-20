package dev.langchain4j.store.graph.neo4j;

import static dev.langchain4j.Neo4jTestUtils.getNeo4jContainer;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Neo4jGraphIT {

    @Container
    private static final Neo4jContainer<?> neo4jContainer =
            getNeo4jContainer().withoutAuthentication().withLabsPlugins("apoc");

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
        String expectedSchema =
                "Node properties are the following:\n\n\nRelationship properties are the following:\n\n\nThe relationships are the following:\n";
        assertThat(neo4jGraph.getSchema()).isEqualTo(expectedSchema);
    }

    @Test
    @Order(2)
    void executeWriteShouldExecuteQuery() {

        String query = "CREATE (n:Person {name: 'John'})";
        ResultSummary resultSummary = neo4jGraph.executeWrite(query);

        assertThat(resultSummary.counters().nodesCreated()).isEqualTo(1);
    }

    @Test
    @Order(3)
    void executeReadShouldReturnRecords() {

        String query = "MATCH (n:Person) RETURN n";
        List<Record> records = neo4jGraph.executeRead(query);

        assertThat(records.get(0).get("n").asNode().get("name").asString()).isEqualTo("John");
    }

    @Test
    @Order(4)
    void refreshSchemaShouldReturnUpdatedSchema() {

        neo4jGraph.refreshSchema();
        String expectedSchema =
                """
                Node properties are the following:
                Person {name:STRING}

                Relationship properties are the following:


                The relationships are the following:
                """;
        assertThat(neo4jGraph.getSchema()).isEqualTo(expectedSchema);
    }
}
