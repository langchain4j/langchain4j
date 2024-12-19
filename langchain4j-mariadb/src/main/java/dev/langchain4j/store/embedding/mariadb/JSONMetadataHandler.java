package dev.langchain4j.store.embedding.mariadb;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import java.sql.*;
import java.util.*;

/**
 * Handle metadata as JSON column.
 */
class JSONMetadataHandler implements MetadataHandler {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);
    public static final String DEFAULT_COLUMN_METADATA = "metadata";
    private final MetadataColumDefinition columnDefinition;
    private final String escapedColumnsName;
    private final JSONFilterMapper filterMapper;
    private final List<String> indexes;

    /**
     * MetadataHandler constructor
     * @param config {@link MetadataStorageConfig} configuration
     */
    public JSONMetadataHandler(MetadataStorageConfig config, List<String> sqlKeywords) {
        List<String> definition = ensureNotEmpty(config.columnDefinitions(), "Metadata definition");
        if (definition.size() > 1) {
            throw new IllegalArgumentException(
                    "Metadata definition should be an unique column definition, " + "example: metadata JSON NULL");
        }
        this.columnDefinition = MetadataColumDefinition.from(definition.get(0), sqlKeywords);
        if (this.columnDefinition.escapedName() == null) {
            this.escapedColumnsName = DEFAULT_COLUMN_METADATA;
        } else {
            this.escapedColumnsName = this.columnDefinition.escapedName();
        }

        this.filterMapper = new JSONFilterMapper(escapedColumnsName);
        this.indexes = getOrDefault(config.indexes(), Collections.emptyList());
    }

    @Override
    public String columnDefinitionsString() {
        return columnDefinition.fullDefinition();
    }

    @Override
    public List<String> escapedColumnsName() {
        return Collections.singletonList(this.escapedColumnsName);
    }

    @Override
    public void createMetadataIndexes(Statement statement, String table) {
        if (!this.indexes.isEmpty()) {
            throw new RuntimeException("Indexes are actually not allowed for JSON metadata");
        }
    }

    @Override
    public String whereClause(Filter filter) {
        return filterMapper.map(filter);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Metadata fromResultSet(ResultSet resultSet) {
        try {
            String metadataJson = getOrDefault(resultSet.getString(5), "{}");
            return new Metadata(OBJECT_MAPPER.readValue(metadataJson, Map.class));
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String insertClause() {
        return ", " + String.format("%s = VALUES(%s)", this.escapedColumnsName, this.escapedColumnsName);
    }

    @Override
    public void setMetadata(PreparedStatement upsertStmt, Integer parameterInitialIndex, Metadata metadata) {
        try {
            String jsonValue = OBJECT_MAPPER.writeValueAsString(metadata.toMap());
            upsertStmt.setString(parameterInitialIndex, jsonValue);
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
