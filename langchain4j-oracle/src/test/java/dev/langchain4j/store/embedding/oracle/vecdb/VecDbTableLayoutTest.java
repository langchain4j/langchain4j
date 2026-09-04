package dev.langchain4j.store.embedding.oracle.vecdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

/** Verifies classification of earlier, newer, partially migrated, and incompatible table layouts. */
class VecDbTableLayoutTest {

    /** Verifies recognition of the 23.26.1/23.26.2 physical table layout. */
    @Test
    void testClassifiesLegacyLayout() {
        VecDbTableLayout layout = layout("ID", "DENSE_VECTOR", "METADATA");

        assertThat(layout.state()).isEqualTo(VecDbTableLayout.LayoutState.LEGACY);
    }

    /** Verifies recognition of the physical table layout introduced in 23.26.3. */
    @Test
    void testClassifiesV23263Layout() {
        VecDbTableLayout layout =
                layout("ID", "DENSE_VECTOR", "CONTENT_METADATA", "SPARSE_VECTOR", "CONTENT", "CONTENT_TYPE");

        assertThat(layout.state()).isEqualTo(VecDbTableLayout.LayoutState.CURRENT);
    }

    /** Verifies recognition of an earlier layout with only some migration actions applied. */
    @Test
    void testClassifiesPartiallyMigratedLegacyLayout() {
        VecDbTableLayout layout = layout("ID", "DENSE_VECTOR", "METADATA", "SPARSE_VECTOR", "CONTENT");

        assertThat(layout.state()).isEqualTo(VecDbTableLayout.LayoutState.PARTIALLY_MIGRATED);
    }

    /** Verifies recognition of a newer layout that is still missing optional columns. */
    @Test
    void testClassifiesPartiallyMigratedV23263Layout() {
        VecDbTableLayout layout = layout("ID", "DENSE_VECTOR", "CONTENT_METADATA", "CONTENT");

        assertThat(layout.state()).isEqualTo(VecDbTableLayout.LayoutState.PARTIALLY_MIGRATED);
    }

    /** Verifies that a table missing essential ID or vector columns is incompatible. */
    @Test
    void testClassifiesMissingEssentialColumnsAsIncompatible() {
        assertThat(layout("ID", "METADATA").state()).isEqualTo(VecDbTableLayout.LayoutState.INCOMPATIBLE);
        assertThat(layout("DENSE_VECTOR", "METADATA").state()).isEqualTo(VecDbTableLayout.LayoutState.INCOMPATIBLE);
    }

    /** Verifies that old and new metadata columns cannot coexist in a compatible layout. */
    @Test
    void testClassifiesConflictingMetadataColumnsAsIncompatible() {
        VecDbTableLayout layout = layout("ID", "DENSE_VECTOR", "METADATA", "CONTENT_METADATA");

        assertThat(layout.state()).isEqualTo(VecDbTableLayout.LayoutState.INCOMPATIBLE);
    }

    /** Verifies that JDBC column-name casing does not affect layout classification. */
    @Test
    void testNormalizesColumnNamesCaseInsensitively() {
        VecDbTableLayout layout = layout("id", "Dense_Vector", "metadata");

        assertThat(layout.state()).isEqualTo(VecDbTableLayout.LayoutState.LEGACY);
        assertThat(layout.hasColumn("dense_vector")).isTrue();
        assertThat(layout.columns()).containsExactlyInAnyOrder("ID", "DENSE_VECTOR", "METADATA");
    }

    /** Verifies that blank column names are rejected before classification. */
    @Test
    void testRejectsBlankColumnName() {
        assertThatThrownBy(() -> layout("ID", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("column");
    }

    private static VecDbTableLayout layout(String... columns) {
        return new VecDbTableLayout(Set.of(columns));
    }
}
