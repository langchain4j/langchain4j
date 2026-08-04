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

class VecDbTableMigrationTest {

    @Test
    void testDoesNotMigrateLegacyLayoutForLegacyApi() {
        VecDbTableMigration migration = determine(VecDbApiVersion.V23_26_1, "ID", "DENSE_VECTOR", "METADATA");

        assertThat(migration.isRequired()).isFalse();
        assertThat(migration.actions()).isEmpty();
    }

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

    @Test
    void testPlansFullLegacyMigrationForNewApi() {
        VecDbTableMigration migration = determine(VecDbApiVersion.V23_26_3, "ID", "DENSE_VECTOR", "METADATA");

        assertThat(migration.actions())
                .containsExactly(RENAME_METADATA_TO_CONTENT_METADATA, ADD_SPARSE_VECTOR, ADD_CONTENT, ADD_CONTENT_TYPE);
    }

    @Test
    void testPlansOnlyMissingActionsForPartiallyMigratedLegacyLayout() {
        VecDbTableMigration migration =
                determine(VecDbApiVersion.V23_26_3, "ID", "DENSE_VECTOR", "METADATA", "SPARSE_VECTOR", "CONTENT");

        assertThat(migration.actions()).containsExactly(RENAME_METADATA_TO_CONTENT_METADATA, ADD_CONTENT_TYPE);
    }

    @Test
    void testPlansOnlyMissingActionsForPartiallyMigratedV23263Layout() {
        VecDbTableMigration migration =
                determine(VecDbApiVersion.V23_26_3, "ID", "DENSE_VECTOR", "CONTENT_METADATA", "CONTENT");

        assertThat(migration.actions()).containsExactly(ADD_SPARSE_VECTOR, ADD_CONTENT_TYPE);
    }

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

    @Test
    void testRejectsPartiallyMigratedLayoutForLegacyApi() {
        assertThatThrownBy(() -> determine(VecDbApiVersion.V23_26_1, "ID", "DENSE_VECTOR", "METADATA", "CONTENT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("requires the legacy table layout");
    }

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
