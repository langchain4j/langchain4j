package dev.langchain4j.store.embedding.pgvector;

import static dev.langchain4j.internal.Utils.isNullOrBlank;

import java.util.Locale;
import java.util.Set;

final class PgVectorMetadataIndexValidator {

    private static final Set<String> INDEX_TYPES = Set.of("btree", "hash", "gin", "gist", "brin", "spgist");

    private PgVectorMetadataIndexValidator() {}

    static String indexTypeSql(String indexType) {
        if (indexType == null) {
            return "";
        }

        if (isNullOrBlank(indexType)) {
            throw new IllegalArgumentException("Invalid metadata index type: '" + indexType + "'");
        }

        String trimmed = indexType.trim();
        if (!INDEX_TYPES.contains(trimmed.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Invalid metadata index type: '" + indexType + "'");
        }
        return "USING " + trimmed;
    }
}
