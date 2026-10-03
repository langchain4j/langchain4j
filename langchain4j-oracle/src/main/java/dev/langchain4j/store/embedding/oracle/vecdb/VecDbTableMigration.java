package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.oracle.vecdb.VecDbTableLayout.CONTENT;
import static dev.langchain4j.store.embedding.oracle.vecdb.VecDbTableLayout.CONTENT_METADATA;
import static dev.langchain4j.store.embedding.oracle.vecdb.VecDbTableLayout.CONTENT_TYPE;
import static dev.langchain4j.store.embedding.oracle.vecdb.VecDbTableLayout.LEGACY_METADATA;
import static dev.langchain4j.store.embedding.oracle.vecdb.VecDbTableLayout.SPARSE_VECTOR;

import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbApiVersion;
import java.util.ArrayList;
import java.util.List;

/** Ordered schema changes required to make a vector table compatible with a VecDB API generation. */
record VecDbTableMigration(List<Action> actions) {

    private static final VecDbTableMigration NONE = new VecDbTableMigration(List.of());

    VecDbTableMigration {
        actions = List.copyOf(ensureNotNull(actions, "actions"));
    }

    static VecDbTableMigration determine(VecDbApiVersion apiVersion, VecDbTableLayout layout) {
        ensureNotNull(apiVersion, "apiVersion");
        ensureNotNull(layout, "layout");

        return switch (apiVersion) {
            case V23_26_1 -> forLegacyApi(layout);
            case V23_26_3 -> forNewApi(layout);
        };
    }

    boolean isRequired() {
        return !actions.isEmpty();
    }

    private static VecDbTableMigration forLegacyApi(VecDbTableLayout layout) {
        if (layout.state() == VecDbTableLayout.LayoutState.LEGACY) {
            return NONE;
        }
        throw incompatible(
                "database version 23.26.1 requires the legacy table layout and automatic downgrade is not supported",
                layout);
    }

    private static VecDbTableMigration forNewApi(VecDbTableLayout layout) {
        if (layout.state() == VecDbTableLayout.LayoutState.CURRENT) {
            return NONE;
        }
        if (layout.state() == VecDbTableLayout.LayoutState.INCOMPATIBLE) {
            throw incompatible("table cannot be migrated safely to the 23.26.3 layout", layout);
        }

        List<Action> actions = new ArrayList<>();
        if (layout.hasColumn(LEGACY_METADATA) && !layout.hasColumn(CONTENT_METADATA)) {
            actions.add(Action.RENAME_METADATA_TO_CONTENT_METADATA);
        }
        if (!layout.hasColumn(SPARSE_VECTOR)) {
            actions.add(Action.ADD_SPARSE_VECTOR);
        }
        if (!layout.hasColumn(CONTENT)) {
            actions.add(Action.ADD_CONTENT);
        }
        if (!layout.hasColumn(CONTENT_TYPE)) {
            actions.add(Action.ADD_CONTENT_TYPE);
        }
        return new VecDbTableMigration(actions);
    }

    private static IllegalStateException incompatible(String reason, VecDbTableLayout layout) {
        return new IllegalStateException(
                "Incompatible VecDB table layout: " + reason + "; detected columns " + layout.columns());
    }

    enum Action {
        RENAME_METADATA_TO_CONTENT_METADATA,
        ADD_SPARSE_VECTOR,
        ADD_CONTENT,
        ADD_CONTENT_TYPE
    }
}
