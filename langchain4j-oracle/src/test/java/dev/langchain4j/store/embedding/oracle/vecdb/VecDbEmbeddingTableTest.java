package dev.langchain4j.store.embedding.oracle.vecdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Verifies validation of vector-table configuration. */
class VecDbEmbeddingTableTest {

    /** Verifies the unquoted Oracle identifier forms accepted for VecDB table names. */
    @ParameterizedTest
    @ValueSource(strings = {"VECTORS", "vectors_2026", "V$VECTORS", "VECTOR#1"})
    void testAcceptsOracleIdentifiers(String name) {
        VecDbEmbeddingTable table = VecDbEmbeddingTable.builder().name(name).build();

        assertThat(table.name()).isEqualTo(name);
    }

    /** Verifies rejection of identifier forms that could change a dynamically constructed SQL statement. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "1VECTORS",
                "_VECTORS",
                "OWNER.VECTORS",
                "\"Quoted Vectors\"",
                "VECTORS WHERE 1=1",
                "VECTORS; DROP TABLE USERS",
                "VECTORS--comment"
            })
    void testRejectsUnsupportedOracleIdentifiers(String name) {
        assertThatThrownBy(() -> VecDbEmbeddingTable.builder().name(name))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unquoted Oracle identifier");
    }

    /** Verifies Oracle's 128-byte identifier limit for the accepted ASCII identifier form. */
    @ParameterizedTest
    @ValueSource(ints = {129, 256})
    void testRejectsOracleIdentifiersLongerThan128Bytes(int length) {
        String name = "V".repeat(length);

        assertThatThrownBy(() -> VecDbEmbeddingTable.builder().name(name))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 128 ASCII characters");
    }
}
