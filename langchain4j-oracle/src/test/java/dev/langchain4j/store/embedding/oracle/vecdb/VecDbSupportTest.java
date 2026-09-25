package dev.langchain4j.store.embedding.oracle.vecdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbApiVersion;
import org.junit.jupiter.api.Test;

/** Verifies database-version parsing, minimum-version enforcement, and API dialect selection. */
class VecDbSupportTest {

    /** Verifies that Oracle 23.26.1 and 23.26.2 select the earlier VecDB API dialect. */
    @Test
    void testResolvesLegacyApiVersion() {
        assertThat(resolve("23.26.1.0.0")).isEqualTo(VecDbApiVersion.V23_26_1);
        assertThat(resolve("23.26.2.0.0")).isEqualTo(VecDbApiVersion.V23_26_1);
    }

    /** Verifies that Oracle 23.26.3 and later select the newer VecDB API dialect. */
    @Test
    void testResolvesCurrentApiVersion() {
        assertThat(resolve("23.26.3.0.0")).isEqualTo(VecDbApiVersion.V23_26_3);
        assertThat(resolve("23.27.0.0.0")).isEqualTo(VecDbApiVersion.V23_26_3);
        assertThat(resolve("24.1.0.0.0")).isEqualTo(VecDbApiVersion.V23_26_3);
    }

    /** Verifies that database versions predating VecDB are rejected. */
    @Test
    void testRejectsDatabaseVersionBeforeVecDbIntroduction() {
        assertThatThrownBy(() -> resolve("23.26.0.0.0"))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("23.26.1 or later")
                .hasMessageContaining("23.26.0");
    }

    /** Verifies that the last complete version in an Oracle product string controls dialect selection. */
    @Test
    void testUsesLastFullVersionFromOracleProductString() {
        String productVersion = "Oracle Database 23ai Release 23.0.0.0.0 - Production Version 23.26.3.0.0";

        assertThat(resolve(productVersion)).isEqualTo(VecDbApiVersion.V23_26_3);
    }

    /** Verifies that an unrecognizable database product version fails with a clear error. */
    @Test
    void testRejectsUnparseableDatabaseVersion() {
        assertThatThrownBy(() -> VecDbSupport.parseVersion("Oracle Database version unavailable"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to determine Oracle Database version");
    }

    private static VecDbApiVersion resolve(String productVersion) {
        return VecDbSupport.resolveApiVersion(VecDbSupport.parseVersion(productVersion));
    }
}
