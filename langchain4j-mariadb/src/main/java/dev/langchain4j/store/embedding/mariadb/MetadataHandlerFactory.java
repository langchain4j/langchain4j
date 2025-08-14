package dev.langchain4j.store.embedding.mariadb;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.sql.DataSource;

/**
 * MetadataHandlerFactory class
 * Use the {@link MetadataStorageConfig#storageMode()} to switch between different Handler implementation
 */
class MetadataHandlerFactory {
    /**
     * Default Constructor
     */
    public MetadataHandlerFactory() {}

    /**
     * Retrieve the handler associated to the config
     * @param config MetadataConfig config
     * @param dataSource datasource
     * @return MetadataHandler
     */
    static MetadataHandler get(MetadataStorageConfig config, DataSource dataSource) {

        List<String> sqlKeywords = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            sqlKeywords = Arrays.stream(
                            connection.getMetaData().getSQLKeywords().split(","))
                    .map(str -> str.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            // eat
        }

        switch (config.storageMode()) {
            case COMBINED_JSON:
                return new JSONMetadataHandler(config, sqlKeywords);
            case COLUMN_PER_KEY:
                return new ColumnsMetadataHandler(config, sqlKeywords);
            default:
                throw new RuntimeException(String.format("Type %s not handled.", config.storageMode()));
        }
    }
}
