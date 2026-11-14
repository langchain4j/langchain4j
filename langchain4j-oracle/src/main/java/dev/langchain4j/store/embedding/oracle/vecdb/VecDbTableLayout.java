package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Physical columns detected in an existing VecDB vector table. */
record VecDbTableLayout(Set<String> columns) {

    static final String ID = "ID";
    static final String DENSE_VECTOR = "DENSE_VECTOR";
    static final String LEGACY_METADATA = "METADATA";
    static final String CONTENT_METADATA = "CONTENT_METADATA";
    static final String SPARSE_VECTOR = "SPARSE_VECTOR";
    static final String CONTENT = "CONTENT";
    static final String CONTENT_TYPE = "CONTENT_TYPE";

    VecDbTableLayout {
        ensureNotNull(columns, "columns");
        Set<String> normalizedColumns = new LinkedHashSet<>();
        for (String column : columns) {
            normalizedColumns.add(ensureNotBlank(column, "column").toUpperCase(Locale.ROOT));
        }
        columns = Set.copyOf(normalizedColumns);
    }

    boolean hasColumn(String column) {
        return columns.contains(ensureNotBlank(column, "column").toUpperCase(Locale.ROOT));
    }

    LayoutState state() {
        if (!hasColumn(ID) || !hasColumn(DENSE_VECTOR)) {
            return LayoutState.INCOMPATIBLE;
        }

        boolean hasLegacyMetadata = hasColumn(LEGACY_METADATA);
        boolean hasContentMetadata = hasColumn(CONTENT_METADATA);
        if (hasLegacyMetadata && hasContentMetadata) {
            return LayoutState.INCOMPATIBLE;
        }

        boolean hasEveryContentColumn = hasColumn(SPARSE_VECTOR) && hasColumn(CONTENT) && hasColumn(CONTENT_TYPE);
        if (hasContentMetadata && hasEveryContentColumn) {
            return LayoutState.CURRENT;
        }

        boolean hasAnyContentColumn = hasColumn(SPARSE_VECTOR) || hasColumn(CONTENT) || hasColumn(CONTENT_TYPE);
        if (hasLegacyMetadata && !hasAnyContentColumn) {
            return LayoutState.LEGACY;
        }
        if (hasLegacyMetadata || hasContentMetadata) {
            return LayoutState.PARTIALLY_MIGRATED;
        }
        return LayoutState.INCOMPATIBLE;
    }

    enum LayoutState {
        LEGACY,
        CURRENT,
        PARTIALLY_MIGRATED,
        INCOMPATIBLE
    }
}
