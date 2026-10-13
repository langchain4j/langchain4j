package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.store.embedding.oracle.vecdb.VecDbTableMigration.Action.ADD_CONTENT;
import static dev.langchain4j.store.embedding.oracle.vecdb.VecDbTableMigration.Action.ADD_CONTENT_TYPE;
import static dev.langchain4j.store.embedding.oracle.vecdb.VecDbTableMigration.Action.ADD_SPARSE_VECTOR;
import static dev.langchain4j.store.embedding.oracle.vecdb.VecDbTableMigration.Action.RENAME_METADATA_TO_CONTENT_METADATA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import oracle.sql.json.OracleJsonObject;
import oracle.sql.json.OracleJsonValue;
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

    /** Verifies that floating-point boundary values are bound as Oracle JSON doubles, not Oracle numbers. */
    @Test
    void testMapsSmallestDoubleToTypedOracleJson() throws SQLException {
        OracleJsonObject json =
                VecDbJdbcQueryExecutor.toOracleJsonValue("{\"value\":4.9E-324}").asJsonObject();

        OracleJsonValue value = json.get("value");
        assertThat(value.getOracleJsonType()).isEqualTo(OracleJsonValue.OracleJsonType.DOUBLE);
        assertThat(value.asJsonDouble().doubleValue()).isEqualTo(Double.MIN_VALUE);
    }

    /** Verifies recursive conversion of objects, arrays, scalar values, and JSON null. */
    @Test
    void testMapsNestedJsonToTypedOracleJson() throws SQLException {
        OracleJsonObject json = VecDbJdbcQueryExecutor.toOracleJsonValue("""
                        {
                          "id": "vector-1",
                          "metadata": {
                            "page": 3,
                            "score": 0.25,
                            "active": true,
                            "optional": null
                          },
                          "dense_vector": [0.1, -0.2]
                        }
                        """).asJsonObject();

        assertThat(json.getString("id")).isEqualTo("vector-1");
        assertThat(json.getObject("metadata").getInt("page")).isEqualTo(3);
        assertThat(json.getObject("metadata").get("score").getOracleJsonType())
                .isEqualTo(OracleJsonValue.OracleJsonType.DOUBLE);
        assertThat(json.getObject("metadata").getBoolean("active")).isTrue();
        assertThat(json.getObject("metadata").isNull("optional")).isTrue();
        assertThat(json.getArray("dense_vector").getDouble(0)).isEqualTo(0.1);
        assertThat(json.getArray("dense_vector").getDouble(1)).isEqualTo(-0.2);
    }

    /** Verifies that malformed request JSON is reported as a JDBC boundary error. */
    @Test
    void testRejectsMalformedJsonParameter() {
        assertThatThrownBy(() -> VecDbJdbcQueryExecutor.toOracleJsonValue("not-json"))
                .isInstanceOf(SQLException.class)
                .hasMessage("Invalid JSON parameter");
    }
}
