package dev.langchain4j.store.embedding.hibernate;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;

import org.hibernate.dialect.DB2Dialect;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.MariaDBDialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.OracleDialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.dialect.SQLServerDialect;

/**
 * Database kind
 * <ul>
 * <li>DB2: The IBM DB2 database
 * <li>MARIADB: The MariaDB database
 * <li>MSSQL: The Microsoft SQL Server database
 * <li>MYSQL: The MySQL database
 * <li>POSTGRESQL: The PostgreSQL database
 * <li>ORACLE: The Oracle database
 * </ul>
 * <p>
 */
public interface DatabaseKind {

    DatabaseKind DB2 = new DatabaseKindImpl(
            "jdbc:db2://{host}:{port}/{database}",
            (distanceFunction, indexType, table, embeddingColumn, indexOptions) -> {
                final String distanceMetric =
                        switch (distanceFunction) {
                            case COSINE -> "cosine";
                            case EUCLIDEAN -> "euclidean";
                            case EUCLIDEAN_SQUARED -> "euclidean_squared";
                            case MANHATTAN -> "manhattan";
                            case HAMMING -> "hamming";
                            case JACCARD -> "jaccard";
                            case INNER_PRODUCT, NEGATIVE_INNER_PRODUCT -> "dot";
                        };
                return "create vector index " + table + "_index on "
                        + table + "(" + embeddingColumn + ") with distance " + distanceMetric + " "
                        + getOrDefault(indexOptions, "") + ";";
            });
    DatabaseKind MARIADB = new DatabaseKindImpl(
            "jdbc:mariadb://{host}:{port}/{database}",
            (distanceFunction, indexType, table, embeddingColumn, indexOptions) -> {
                final String distanceMethod =
                        switch (distanceFunction) {
                            case COSINE -> "cosine";
                            case EUCLIDEAN, EUCLIDEAN_SQUARED -> "euclidean";
                            default ->
                                throw new IllegalArgumentException(
                                        "MariaDB does not support the distance function: " + distanceFunction);
                        };
                return "create vector index if not exists " + table + "_index on "
                        + table + "(" + embeddingColumn + ") distance=" + distanceMethod + " "
                        + getOrDefault(indexOptions, "") + ";";
            });
    DatabaseKind MSSQL = new DatabaseKindImpl(
            "jdbc:sqlserver://{host}:{port};databaseName={database};sendTimeAsDatetime=false;trustServerCertificate=true",
            (distanceFunction, indexType, table, embeddingColumn, indexOptions) -> {
                final String distanceMetric =
                        switch (distanceFunction) {
                            case COSINE -> "'cosine'";
                            case EUCLIDEAN, EUCLIDEAN_SQUARED -> "'euclidean'";
                            case INNER_PRODUCT, NEGATIVE_INNER_PRODUCT -> "'dot'";
                            default ->
                                throw new IllegalArgumentException(
                                        "SQL Server does not support the distance function: " + distanceFunction);
                        };
                return "create vector index " + table + "_index on "
                        + table + "(" + embeddingColumn + ") with (metric=" + distanceMetric
                        + (isNullOrBlank(indexOptions) ? "" : ("," + indexOptions)) + ");";
            });
    DatabaseKind MYSQL = new DatabaseKindImpl(
            "jdbc:mysql://{host}:{port}/{database}?allowPublicKeyRetrieval=true",
            // MySQL HeatWave creates indexes automatically
            (distanceFunction, indexType, table, embeddingColumn, indexOptions) -> null);
    DatabaseKind POSTGRESQL = new DatabaseKindImpl(
            "jdbc:postgresql://{host}:{port}/{database}",
            (distanceFunction, indexType, table, embeddingColumn, indexOptions) -> {
                final String vectorOps =
                        switch (distanceFunction) {
                            case COSINE -> "vector_cosine_ops";
                            case EUCLIDEAN, EUCLIDEAN_SQUARED -> "vector_l2_ops";
                            case MANHATTAN -> "vector_l1_ops";
                            case HAMMING -> "vector_hamming_ops";
                            case JACCARD -> "vector_jaccard_ops";
                            case INNER_PRODUCT, NEGATIVE_INNER_PRODUCT -> "vector_ip_ops";
                        };
                final String indexMethod = indexType == null ? "ivfflat" : indexType;
                return "create index if not exists " + table + "_" + indexMethod + "_index on "
                        + table + " using " + indexMethod + "(" + embeddingColumn + " " + vectorOps
                        + ") with (" + getOrDefault(indexOptions, "") + ");";
            },
            "create extension if not exists vector;");
    DatabaseKind ORACLE = new DatabaseKindImpl(
            "jdbc:oracle:thin:@{host}:{port}/{database}",
            (distanceFunction, indexType, table, embeddingColumn, indexOptions) -> {
                final String distanceMetric =
                        switch (distanceFunction) {
                            case COSINE -> "cosine";
                            case EUCLIDEAN -> "euclidean";
                            case EUCLIDEAN_SQUARED -> "euclidean_squared";
                            case MANHATTAN -> "manhattan";
                            case HAMMING -> "hamming";
                            case JACCARD -> "jaccard";
                            case INNER_PRODUCT, NEGATIVE_INNER_PRODUCT -> "dot";
                        };
                return "create vector index if not exists " + table + "_index on "
                        + table + "(" + embeddingColumn + ") organization neighbor partitions with distance "
                        + distanceMetric + " "
                        + getOrDefault(indexOptions, "") + ";";
            });

    String createJdbcUrl(String host, int port, String database);

    boolean isJdbcUrl(String jdbcUrl);

    String createIndexDDL(
            DistanceFunction distanceFunction,
            String indexType,
            String table,
            String embeddingColumn,
            String indexOptions);

    String getSetupSql();

    static DatabaseKind determineDatabaseKind(String jdbcUrl) {
        if (DB2.isJdbcUrl(jdbcUrl)) {
            return DatabaseKind.DB2;
        } else if (MARIADB.isJdbcUrl(jdbcUrl)) {
            return DatabaseKind.MARIADB;
        } else if (MSSQL.isJdbcUrl(jdbcUrl)) {
            return DatabaseKind.MSSQL;
        } else if (MYSQL.isJdbcUrl(jdbcUrl)) {
            return DatabaseKind.MYSQL;
        } else if (POSTGRESQL.isJdbcUrl(jdbcUrl)) {
            return DatabaseKind.POSTGRESQL;
        } else if (ORACLE.isJdbcUrl(jdbcUrl)) {
            return DatabaseKind.ORACLE;
        } else {
            return null;
        }
    }

    static DatabaseKind determineDatabaseKind(Dialect dialect) {
        if (dialect instanceof DB2Dialect) {
            return DatabaseKind.DB2;
        } else if (dialect instanceof MariaDBDialect) {
            return DatabaseKind.MARIADB;
        } else if (dialect instanceof SQLServerDialect) {
            return DatabaseKind.MSSQL;
        } else if (dialect instanceof MySQLDialect) {
            return DatabaseKind.MYSQL;
        } else if (dialect instanceof PostgreSQLDialect) {
            return DatabaseKind.POSTGRESQL;
        } else if (dialect instanceof OracleDialect) {
            return DatabaseKind.ORACLE;
        } else {
            return null;
        }
    }
}
