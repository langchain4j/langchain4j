package dev.langchain4j.graph.neo4j;

import lombok.Builder;
import lombok.Getter;
import org.neo4j.driver.Record;
import org.neo4j.driver.*;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.summary.ResultSummary;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

public class Neo4jGraph implements AutoCloseable {

    private static final String NODE_PROPERTIES_QUERY = """
            CALL apoc.meta.data()
            YIELD label, other, elementType, type, property
            WHERE NOT type = "RELATIONSHIP" AND elementType = "node"
            WITH label AS nodeLabels, collect({property:property, type:type}) AS properties
            RETURN {labels: nodeLabels, properties: properties} AS output
            """;

    private static final String REL_PROPERTIES_QUERY = """
            CALL apoc.meta.data()
            YIELD label, other, elementType, type, property
            WHERE NOT type = "RELATIONSHIP" AND elementType = "relationship"
            WITH label AS nodeLabels, collect({property:property, type:type}) AS properties
            RETURN {type: nodeLabels, properties: properties} AS output
            """;

    private static final String RELATIONSHIPS_QUERY = """
            CALL apoc.meta.data()
            YIELD label, other, elementType, type, property
            WHERE type = "RELATIONSHIP" AND elementType = "node"
            UNWIND other AS other_node
            RETURN {start: label, type: property, end: toString(other_node)} AS output
            """;

    private final Driver driver;

    @Getter
    private String schema;

    @Builder
    public Neo4jGraph(final Driver driver) {

        this.driver = ensureNotNull(driver, "driver");
        this.driver.verifyConnectivity();
        try {
            refreshSchema();
        } catch (ClientException e) {
            if ("Neo.ClientError.Procedure.ProcedureNotFound".equals(e.code())) {
                throw new RuntimeException("Please ensure the APOC plugin is installed in Neo4j", e);
            }
            throw e;
        }
    }

    public ResultSummary executeWrite(String queryString) {

        try (Session session = this.driver.session()) {
            return session.executeWrite(tx -> tx.run(queryString).consume());
        }
    }

    public List<Record> executeRead(String queryString) {

        try (Session session = this.driver.session()) {
            return session.executeRead(tx -> {
                Query query = new Query(queryString);
                Result result = tx.run(query);
                return result.list();
            });
        }
    }

    public void refreshSchema() {

        List<String> nodeProperties = formatNodeProperties(executeRead(NODE_PROPERTIES_QUERY));
        List<String> relationshipProperties = formatRelationshipProperties(executeRead(REL_PROPERTIES_QUERY));
        List<String> relationships = formatRelationships(executeRead(RELATIONSHIPS_QUERY));

        this.schema = "Node properties are the following:\n" +
                String.join("\n", nodeProperties) + "\n\n" +
                "Relationship properties are the following:\n" +
                String.join("\n", relationshipProperties) + "\n\n" +
                "The relationships are the following:\n" +
                String.join("\n", relationships);
    }

    private List<String> formatNodeProperties(List<Record> records) {

        return records.stream()
                .map(this::getOutput)
                .map(r -> String.format("%s %s", r.asMap().get("labels"), formatMap(r.get("properties").asList(Value::asMap))))
                .toList();
    }

    private List<String> formatRelationshipProperties(List<Record> records) {

        return records.stream()
                .map(this::getOutput)
                .map(r -> String.format("%s %s", r.get("type"), formatMap(r.get("properties").asList(Value::asMap))))
                .toList();
    }

    private List<String> formatRelationships(List<Record> records) {

        return records.stream()
                .map(r -> getOutput(r).asMap())
                .map(r -> String.format("(:%s)-[:%s]->(:%s)", r.get("start"), r.get("type"), r.get("end")))
                .toList();
    }

    private Value getOutput(Record record) {

        return record.get("output");
    }

    private String formatMap(List<Map<String, Object>> properties) {

        return properties.stream()
                .map(prop -> prop.get("property") + ":" + prop.get("type"))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    @Override
    public void close() {

        this.driver.close();
    }
}
