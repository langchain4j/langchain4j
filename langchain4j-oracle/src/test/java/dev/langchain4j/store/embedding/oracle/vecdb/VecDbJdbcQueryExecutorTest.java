package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.store.embedding.oracle.vecdb.VecDbTableMigration.Action.ADD_CONTENT;
import static dev.langchain4j.store.embedding.oracle.vecdb.VecDbTableMigration.Action.ADD_CONTENT_TYPE;
import static dev.langchain4j.store.embedding.oracle.vecdb.VecDbTableMigration.Action.ADD_SPARSE_VECTOR;
import static dev.langchain4j.store.embedding.oracle.vecdb.VecDbTableMigration.Action.RENAME_METADATA_TO_CONTENT_METADATA;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Verifies the SQL statements used by the JDBC adapter to migrate earlier VecDB tables. */
class VecDbJdbcQueryExecutorTest {

    private static final String TABLE_NAME = "MY_VECTORS";

    /** Verifies the SQL used to rename the legacy metadata column. */
    @Test
    void testMapsMetadataRenameMigrationToSql() {
        assertThat(VecDbJdbcQueryExecutor.tableMigrationSql(TABLE_NAME, RENAME_METADATA_TO_CONTENT_METADATA))
                .isEqualTo("ALTER TABLE MY_VECTORS RENAME COLUMN METADATA TO CONTENT_METADATA");
    }

    /** Verifies the SQL used to add the sparse-vector column required by the newer layout. */
    @Test
    void testMapsSparseVectorMigrationToSql() {
        assertThat(VecDbJdbcQueryExecutor.tableMigrationSql(TABLE_NAME, ADD_SPARSE_VECTOR))
                .isEqualTo("ALTER TABLE MY_VECTORS ADD SPARSE_VECTOR VECTOR(*, *, SPARSE)");
    }

    /** Verifies the SQL used to add the content BLOB required by the newer layout. */
    @Test
    void testMapsContentMigrationToSql() {
        assertThat(VecDbJdbcQueryExecutor.tableMigrationSql(TABLE_NAME, ADD_CONTENT))
                .isEqualTo("ALTER TABLE MY_VECTORS ADD CONTENT BLOB");
    }

    /** Verifies the SQL used to add the content MIME-type column required by the newer layout. */
    @Test
    void testMapsContentTypeMigrationToSql() {
        assertThat(VecDbJdbcQueryExecutor.tableMigrationSql(TABLE_NAME, ADD_CONTENT_TYPE))
                .isEqualTo("ALTER TABLE MY_VECTORS ADD CONTENT_TYPE VARCHAR2(256)");
    }
}
