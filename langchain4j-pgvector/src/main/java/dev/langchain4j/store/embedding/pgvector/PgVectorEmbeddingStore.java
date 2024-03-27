package dev.langchain4j.store.embedding.pgvector;

import lombok.Builder;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * PGVector EmbeddingStore Implementation
 * <p>
 * Needs all parameter to initialize a database connection
 */
public class PgVectorEmbeddingStore extends DataSourcePgVectorEmbeddingStore {

    /**
     * Constructor for PgVectorEmbeddingStore Class when you don't have already datasource management.
     *
     * @param host                  The database host
     * @param port                  The database port
     * @param user                  The database user
     * @param password              The database password
     * @param database              The database name
     * @param table                 The database table
     * @param dimension             The vector dimension
     * @param useIndex              Should use <a href="https://github.com/pgvector/pgvector#ivfflat">IVFFlat</a> index
     * @param indexListSize         The IVFFlat number of lists
     * @param createTable           Should create table automatically
     * @param dropTableFirst        Should drop table first, usually for testing
     * @param metadataConfig        The {@link MetadataConfig} config.
     */
    @Builder
    public PgVectorEmbeddingStore(
            String host,
            Integer port,
            String user,
            String password,
            String database,
            String table,
            Integer dimension,
            Boolean useIndex,
            Integer indexListSize,
            Boolean createTable,
            Boolean dropTableFirst,
            MetadataConfig metadataConfig
    ) {
        super(createDataSource(host, port, user, password, database),
                table, dimension, useIndex, indexListSize, createTable, dropTableFirst, metadataConfig);
    }

    private static DataSource createDataSource(String host, Integer port, String user, String password, String database) {
        host = ensureNotBlank(host, "host");
        port = ensureGreaterThanZero(port, "port");
        user = ensureNotBlank(user, "user");
        password = ensureNotBlank(password, "password");
        database = ensureNotBlank(database, "database");

        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setServerNames(new String[] {host});
        source.setPortNumbers(new int[] {port});
        source.setDatabaseName(database);
        source.setUser(user);
        source.setPassword(password);

        return source;
    }

}
