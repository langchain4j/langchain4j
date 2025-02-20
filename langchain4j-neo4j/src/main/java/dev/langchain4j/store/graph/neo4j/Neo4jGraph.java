package dev.langchain4j.store.graph.neo4j;

import dev.langchain4j.transformer.GraphDocument;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Getter;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Query;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.summary.ResultSummary;

public class Neo4jGraph extends BaseNeo4jBuilder implements AutoCloseable {

    public static final String DEFAULT_ENTITY_LABEL = "__Entity__";
    private static final String NODE_PROPERTIES_QUERY =
            """
            CALL apoc.meta.data()
            YIELD label, other, elementType, type, property
            WHERE NOT type = "RELATIONSHIP" AND elementType = "node"
            WITH label AS nodeLabels, collect({property:property, type:type}) AS properties
            RETURN {labels: nodeLabels, properties: properties} AS output
            """;

    private static final String REL_PROPERTIES_QUERY =
            """
            CALL apoc.meta.data()
            YIELD label, other, elementType, type, property
            WHERE NOT type = "RELATIONSHIP" AND elementType = "relationship"
            WITH label AS nodeLabels, collect({property:property, type:type}) AS properties
            RETURN {type: nodeLabels, properties: properties} AS output
            """;

    private static final String RELATIONSHIPS_QUERY =
            """
            CALL apoc.meta.data()
            YIELD label, other, elementType, type, property
            WHERE type = "RELATIONSHIP" AND elementType = "node"
            UNWIND other AS other_node
            RETURN {start: label, type: property, end: toString(other_node)} AS output
            """;

    @Getter
    private String schema;

    /**
     * Creates an instance of Neo4jGraph defining a {@link Driver}
     * starting from uri, user and password
     */
    public static class Neo4jGraphBuilder {
        public Neo4jGraphBuilder withBasicAuth(String uri, String user, String password) {
            return this.driver(GraphDatabase.driver(uri, AuthTokens.basic(user, password)));
        }
    }

    /**
     * Creates an instance of Neo4jGraph
     *
     * @param driver: the {@link Driver} (required)
     * @param config: the {@link SessionConfig}  (optional, default is `SessionConfig.forDatabase(`databaseName`)`)
     * @param databaseName: the optional database name (default: "neo4j")
     * @param label: the optional label name (default: "__Entity__")
     * @param idProperty: the optional id property name (default: "id")
     * @param textProperty: the optional textProperty property name (default: "text")
     */
    @Builder
    public Neo4jGraph(
            SessionConfig config,
            String databaseName,
            Driver driver,
            String label,
            String idProperty,
            String textProperty) {
        super(config, databaseName, driver, label, idProperty, textProperty);

        try {
            refreshSchema();
        } catch (ClientException e) {
            if ("Neo.ClientError.Procedure.ProcedureNotFound".equals(e.code())) {
                throw new Neo4jException("Please ensure the APOC plugin is installed in Neo4j", e);
            }
            throw e;
        }
    }

    @Override
    protected String getDefaultLabel() {
        return DEFAULT_ENTITY_LABEL;
    }

    public ResultSummary executeWrite(String queryString) {
        return executeWrite(queryString, Map.of());
    }

    public ResultSummary executeWrite(String queryString, Map<String, Object> params) {

        try (Session session = session()) {
            return session.executeWrite(tx -> {
                tx.run(queryString, params).consume();
                return null;
            });
        } catch (ClientException e) {
            throw new Neo4jException("Error executing query: " + queryString, e);
        }
    }

    public List<Record> executeRead(String queryString) {

        try (Session session = session()) {
            return session.executeRead(tx -> {
                Query query = new Query(queryString);
                Result result = tx.run(query);
                return result.list();
            });
        } catch (ClientException e) {
            throw new Neo4jException("Error executing query: " + queryString, e);
        }
    }

    public void refreshSchema() {

        List<String> nodeProperties = formatNodeProperties(executeRead(NODE_PROPERTIES_QUERY));
        List<String> relationshipProperties = formatRelationshipProperties(executeRead(REL_PROPERTIES_QUERY));
        List<String> relationships = formatRelationships(executeRead(RELATIONSHIPS_QUERY));

        this.schema = "Node properties are the following:\n" + String.join("\n", nodeProperties)
                + "\n\n" + "Relationship properties are the following:\n"
                + String.join("\n", relationshipProperties)
                + "\n\n" + "The relationships are the following:\n"
                + String.join("\n", relationships);
    }

    public void addGraphDocuments(List<GraphDocument> graphDocuments, boolean includeSource, boolean baseEntityLabel) {

        Neo4jGraphUtils.addGraphDocuments(graphDocuments, includeSource, baseEntityLabel, this);
    }

    private List<String> formatNodeProperties(List<Record> records) {

        return records.stream()
                .map(this::getOutput)
                .map(r -> String.format(
                        "%s %s",
                        r.asMap().get("labels"), formatMap(r.get("properties").asList(Value::asMap))))
                .toList();
    }

    private List<String> formatRelationshipProperties(List<Record> records) {

        return records.stream()
                .map(this::getOutput)
                .map(r -> String.format(
                        "%s %s", r.get("type"), formatMap(r.get("properties").asList(Value::asMap))))
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
