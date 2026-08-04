package dev.langchain4j.store.embedding.oracle.vecdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class VecDbTableLayoutTest {

    @Test
    void testClassifiesLegacyLayout() {
        VecDbTableLayout layout = layout("ID", "DENSE_VECTOR", "METADATA");

        assertThat(layout.state()).isEqualTo(VecDbTableLayout.LayoutState.LEGACY);
    }

    @Test
    void testClassifiesV23263Layout() {
        VecDbTableLayout layout =
                layout("ID", "DENSE_VECTOR", "CONTENT_METADATA", "SPARSE_VECTOR", "CONTENT", "CONTENT_TYPE");

        assertThat(layout.state()).isEqualTo(VecDbTableLayout.LayoutState.CURRENT);
    }

    @Test
    void testClassifiesPartiallyMigratedLegacyLayout() {
        VecDbTableLayout layout = layout("ID", "DENSE_VECTOR", "METADATA", "SPARSE_VECTOR", "CONTENT");

        assertThat(layout.state()).isEqualTo(VecDbTableLayout.LayoutState.PARTIALLY_MIGRATED);
    }

    @Test
    void testClassifiesPartiallyMigratedV23263Layout() {
        VecDbTableLayout layout = layout("ID", "DENSE_VECTOR", "CONTENT_METADATA", "CONTENT");

        assertThat(layout.state()).isEqualTo(VecDbTableLayout.LayoutState.PARTIALLY_MIGRATED);
    }

    @Test
    void testClassifiesMissingEssentialColumnsAsIncompatible() {
        assertThat(layout("ID", "METADATA").state()).isEqualTo(VecDbTableLayout.LayoutState.INCOMPATIBLE);
        assertThat(layout("DENSE_VECTOR", "METADATA").state()).isEqualTo(VecDbTableLayout.LayoutState.INCOMPATIBLE);
    }

    @Test
    void testClassifiesConflictingMetadataColumnsAsIncompatible() {
        VecDbTableLayout layout = layout("ID", "DENSE_VECTOR", "METADATA", "CONTENT_METADATA");

        assertThat(layout.state()).isEqualTo(VecDbTableLayout.LayoutState.INCOMPATIBLE);
    }

    @Test
    void testNormalizesColumnNamesCaseInsensitively() {
        VecDbTableLayout layout = layout("id", "Dense_Vector", "metadata");

        assertThat(layout.state()).isEqualTo(VecDbTableLayout.LayoutState.LEGACY);
        assertThat(layout.hasColumn("dense_vector")).isTrue();
        assertThat(layout.columns()).containsExactlyInAnyOrder("ID", "DENSE_VECTOR", "METADATA");
    }

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
