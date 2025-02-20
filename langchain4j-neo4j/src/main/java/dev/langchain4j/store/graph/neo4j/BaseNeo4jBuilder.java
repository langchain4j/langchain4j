package dev.langchain4j.store.graph.neo4j;

import static dev.langchain4j.Neo4jUtils.sanitizeOrThrows;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.neo4j.Neo4jEmbeddingUtils.DEFAULT_DATABASE_NAME;

import lombok.Getter;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;

@Getter
public abstract class BaseNeo4jBuilder {

    /* default configs */
    public static final String DEFAULT_ID_PROP = "id";
    public static final String DEFAULT_TEXT_PROP = "text";

    /* Neo4j Java Driver settings */
    protected final Driver driver;
    protected final SessionConfig config;
    protected final String databaseName;

    /* Neo4j schema field settings */
    protected final String label;
    protected final String idProperty;
    protected final String textProperty;
    protected final String sanitizedLabel;
    protected final String sanitizedIdProperty;
    protected final String sanitizedTextProperty;

    protected BaseNeo4jBuilder(
            SessionConfig config,
            String databaseName,
            Driver driver,
            String label,
            String idProperty,
            String textProperty) {
        /* required configs */
        this.driver = ensureNotNull(driver, "driver");
        this.driver.verifyConnectivity();

        /* optional configs */
        this.databaseName = getOrDefault(databaseName, DEFAULT_DATABASE_NAME);
        this.config = getOrDefault(config, SessionConfig.forDatabase(this.databaseName));
        this.label = getOrDefault(label, getDefaultLabel());
        this.idProperty = getOrDefault(idProperty, DEFAULT_ID_PROP);
        this.textProperty = getOrDefault(textProperty, DEFAULT_TEXT_PROP);

        /* sanitize labels and property names, to prevent from Cypher Injections */
        this.sanitizedLabel = sanitizeOrThrows(this.label, "label");
        this.sanitizedIdProperty = sanitizeOrThrows(this.idProperty, "idProperty");
        this.sanitizedTextProperty = sanitizeOrThrows(this.textProperty, "textProperty");
    }

    protected abstract String getDefaultLabel();

    protected Session session() {
        return this.driver.session(this.config);
    }
}
