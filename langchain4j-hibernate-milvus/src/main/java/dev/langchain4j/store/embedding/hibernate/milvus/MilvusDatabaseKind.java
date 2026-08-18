package dev.langchain4j.store.embedding.hibernate.milvus;

import dev.langchain4j.store.embedding.hibernate.DatabaseKind;
import dev.langchain4j.store.embedding.hibernate.DistanceFunction;

/**
 *
 */
public final class MilvusDatabaseKind implements DatabaseKind {

    public static final DatabaseKind INSTANCE = new MilvusDatabaseKind();

    private MilvusDatabaseKind() {}

    @Override
    public String createIndexDDL(
            final DistanceFunction distanceFunction,
            final String indexType,
            final String table,
            final String embeddingColumn,
            final String indexOptions) {
        return null;
    }

    @Override
    public String getSetupSql() {
        return null;
    }

    @Override
    public String createJdbcUrl(final String host, final int port, final String database) {
        final StringBuilder builder = new StringBuilder();
        builder.append("jdbc:milvus://");
        builder.append(host);
        if (port > 0) {
            builder.append(":");
            builder.append(port);
        }
        builder.append("/");
        builder.append(database);
        return builder.toString();
    }

    @Override
    public boolean isJdbcUrl(final String jdbcUrl) {
        return jdbcUrl.startsWith("jdbc:milvus://");
    }
}
