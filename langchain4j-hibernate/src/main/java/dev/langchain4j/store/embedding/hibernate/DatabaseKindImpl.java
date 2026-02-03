package dev.langchain4j.store.embedding.hibernate;

import java.util.ArrayList;
import java.util.Locale;
import java.util.StringTokenizer;

/**
 *
 */
class DatabaseKindImpl implements DatabaseKind {
    private final String[] jdbcUrlParts;
    private final TemplatePart[] templateParts;
    private final VectorIndexExporter indexExporter;
    private final String setupSql;

    public DatabaseKindImpl(String jdbcUrlTemplate, VectorIndexExporter indexExporter) {
        this(jdbcUrlTemplate, indexExporter, null);
    }

    public DatabaseKindImpl(String jdbcUrlTemplate, VectorIndexExporter indexExporter, String setupSql) {
        final StringTokenizer tokenizer = new StringTokenizer(jdbcUrlTemplate, "{");
        final ArrayList<String> jdbcUrlParts = new ArrayList<>();
        final ArrayList<TemplatePart> templateParts = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            final String token = tokenizer.nextToken();
            if (templateParts.size() < jdbcUrlParts.size()) {
                final int partEnd = token.indexOf('}');
                if (partEnd == -1) {
                    throw new IllegalArgumentException("Invalid JDBC URL template: " + jdbcUrlTemplate);
                }
                templateParts.add(
                        TemplatePart.valueOf(token.substring(0, partEnd).toUpperCase(Locale.ROOT)));
                jdbcUrlParts.add(token.substring(partEnd + 1));
            } else {
                jdbcUrlParts.add(token);
            }
        }
        this.jdbcUrlParts = jdbcUrlParts.toArray(new String[0]);
        this.templateParts = templateParts.toArray(new TemplatePart[0]);
        this.indexExporter = indexExporter;
        this.setupSql = setupSql;
    }

    interface VectorIndexExporter {
        String createIndexDDL(
                DistanceFunction distanceFunction,
                String indexType,
                String table,
                String embeddingColumn,
                String indexOptions);
    }

    @Override
    public String createIndexDDL(
            final DistanceFunction distanceFunction,
            final String indexType,
            final String table,
            final String embeddingColumn,
            final String indexOptions) {
        return indexExporter.createIndexDDL(distanceFunction, indexType, table, embeddingColumn, indexOptions);
    }

    @Override
    public String getSetupSql() {
        return setupSql;
    }

    @Override
    public String createJdbcUrl(final String host, final int port, final String database) {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < jdbcUrlParts.length - 1; i++) {
            builder.append(jdbcUrlParts[i]);
            switch (templateParts[i]) {
                case HOST -> builder.append(host);
                case PORT -> builder.append(port);
                case DATABASE -> builder.append(database);
            }
        }
        builder.append(jdbcUrlParts[jdbcUrlParts.length - 1]);
        return builder.toString();
    }

    @Override
    public boolean isJdbcUrl(final String jdbcUrl) {
        return jdbcUrl.startsWith(jdbcUrlParts[0]);
    }

    enum TemplatePart {
        HOST,
        PORT,
        DATABASE
    }
}
