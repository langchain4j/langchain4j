package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.store.embedding.oracle.vecdb.VecDbTableMigration.Action.ADD_CONTENT;
import static dev.langchain4j.store.embedding.oracle.vecdb.VecDbTableMigration.Action.ADD_CONTENT_TYPE;
import static dev.langchain4j.store.embedding.oracle.vecdb.VecDbTableMigration.Action.ADD_SPARSE_VECTOR;
import static dev.langchain4j.store.embedding.oracle.vecdb.VecDbTableMigration.Action.RENAME_METADATA_TO_CONTENT_METADATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbApiVersion;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifies version-aware planning of physical VecDB table migration actions. */
class VecDbTableMigrationTest {

    /** Verifies that an earlier database keeps an already compatible earlier table unchanged. */
    @Test
    void testDoesNotMigrateLegacyLayoutForLegacyApi() {
        VecDbTableMigration migration = determine(VecDbApiVersion.V23_26_1, "ID", "DENSE_VECTOR", "METADATA");

        assertThat(migration.isRequired()).isFalse();
        assertThat(migration.actions()).isEmpty();
    }

    /** Verifies that a newer database keeps an already compatible newer table unchanged. */
    @Test
    void testDoesNotMigrateCurrentLayoutForNewApi() {
        VecDbTableMigration migration = determine(
                VecDbApiVersion.V23_26_3,
                "ID",
                "DENSE_VECTOR",
                "CONTENT_METADATA",
                "SPARSE_VECTOR",
                "CONTENT",
                "CONTENT_TYPE");

        assertThat(migration.isRequired()).isFalse();
        assertThat(migration.actions()).isEmpty();
    }

    /** Verifies the complete migration plan from the earlier table layout to the newer layout. */
    @Test
    void testPlansFullLegacyMigrationForNewApi() {
        VecDbTableMigration migration = determine(VecDbApiVersion.V23_26_3, "ID", "DENSE_VECTOR", "METADATA");

        assertThat(migration.actions())
                .containsExactly(RENAME_METADATA_TO_CONTENT_METADATA, ADD_SPARSE_VECTOR, ADD_CONTENT, ADD_CONTENT_TYPE);
    }

    /** Verifies that migration schedules only actions missing from a partially migrated earlier table. */
    @Test
    void testPlansOnlyMissingActionsForPartiallyMigratedLegacyLayout() {
        VecDbTableMigration migration =
                determine(VecDbApiVersion.V23_26_3, "ID", "DENSE_VECTOR", "METADATA", "SPARSE_VECTOR", "CONTENT");

        assertThat(migration.actions()).containsExactly(RENAME_METADATA_TO_CONTENT_METADATA, ADD_CONTENT_TYPE);
    }

    /** Verifies that migration adds only the columns missing from a partial newer layout. */
    @Test
    void testPlansOnlyMissingActionsForPartiallyMigratedV23263Layout() {
        VecDbTableMigration migration =
                determine(VecDbApiVersion.V23_26_3, "ID", "DENSE_VECTOR", "CONTENT_METADATA", "CONTENT");

        assertThat(migration.actions()).containsExactly(ADD_SPARSE_VECTOR, ADD_CONTENT_TYPE);
    }

    /** Verifies that the earlier API rejects tables using the newer physical layout. */
    @Test
    void testRejectsCurrentLayoutForLegacyApi() {
        assertThatThrownBy(() -> determine(
                        VecDbApiVersion.V23_26_1,
                        "ID",
                        "DENSE_VECTOR",
                        "CONTENT_METADATA",
                        "SPARSE_VECTOR",
                        "CONTENT",
                        "CONTENT_TYPE"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("automatic downgrade is not supported");
    }

    /** Verifies that the earlier API rejects a table left in a partially migrated state. */
    @Test
    void testRejectsPartiallyMigratedLayoutForLegacyApi() {
        assertThatThrownBy(() -> determine(VecDbApiVersion.V23_26_1, "ID", "DENSE_VECTOR", "METADATA", "CONTENT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires the legacy table layout");
    }

    /** Verifies that the newer API rejects layouts that cannot be migrated safely. */
    @Test
    void testRejectsIncompatibleLayoutForNewApi() {
        assertThatThrownBy(() -> determine(VecDbApiVersion.V23_26_3, "ID", "CONTENT_METADATA"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be migrated safely")
                .hasMessageContaining("CONTENT_METADATA");
    }

    private static VecDbTableMigration determine(VecDbApiVersion apiVersion, String... columns) {
        return VecDbTableMigration.determine(apiVersion, new VecDbTableLayout(Set.of(columns)));
    }
}
