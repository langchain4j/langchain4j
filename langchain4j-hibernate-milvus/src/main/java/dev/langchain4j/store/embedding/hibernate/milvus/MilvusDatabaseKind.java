package dev.langchain4j.store.embedding.hibernate.milvus;

import dev.langchain4j.store.embedding.hibernate.DatabaseKind;
import dev.langchain4j.store.embedding.hibernate.DistanceFunction;
import dev.langchain4j.store.embedding.hibernate.HibernateEmbeddingStore;
import java.util.Map;
import org.hibernate.jpa.HibernateHints;
import org.hibernate.milvus.MilvusDatabaseHints;

/**
 * The database kind for using Milvus.
 *
 * @see HibernateEmbeddingStore.BaseBuilder#databaseKind(DatabaseKind)
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
        // Milvus creates the vector index together with the collection, so there is no index DDL to run
        // afterwards, and the createIndex(), indexType() and indexOptions() settings have no effect
        return null;
    }

    @Override
    public String getSetupSql() {
        return null; // Milvus needs no extension or feature flag to be enabled before vectors can be used
    }

    /**
     * Returns the Milvus session consistency level, so that a search sees all embeddings that this application added
     * or removed before it. Without a consistency level, Milvus uses the default level of the collection, and a search
     * right after {@code add(...)} can miss the new embeddings.
     *
     * @return the session consistency level as a {@link HibernateHints#HINT_QUERY_DATABASE} hint
     */
    @Override
    public Map<String, Object> defaultQueryHints() {
        return Map.of(HibernateHints.HINT_QUERY_DATABASE, MilvusDatabaseHints.CONSISTENCY_LEVEL + "=SESSION");
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
