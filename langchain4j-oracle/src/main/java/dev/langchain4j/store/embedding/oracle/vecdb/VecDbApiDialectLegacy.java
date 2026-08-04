package dev.langchain4j.store.embedding.oracle.vecdb;

import dev.langchain4j.store.embedding.oracle.vecdb.mapper.VecDbIndexJsonMapperLegacy;
import java.sql.CallableStatement;
import java.sql.SQLException;

/** {@code DBMS_VECTOR_DATABASE} API dialect used by Oracle Database 23.26.1 and 23.26.2. */
final class VecDbApiDialectLegacy implements VecDbApiDialect {

    static final VecDbApiDialectLegacy INSTANCE = new VecDbApiDialectLegacy();

    private static final String CREATE_VECTOR_TABLE = "BEGIN ? := DBMS_VECTOR_DATABASE.CREATE_VECTOR_TABLE("
            + "table_name => ?, description => ?, auto_generate_id => FALSE, annotations => ?, "
            + "vector_type => ?, index_params => ?, debug_flags => NULL, request_id => NULL); END;";
    private static final String DESCRIBE_VECTOR_TABLE =
            "BEGIN ? := DBMS_VECTOR_DATABASE.DESCRIBE_VECTOR_TABLE(table_name => ?); END;";
    private static final String DROP_VECTOR_TABLE =
            "BEGIN ? := DBMS_VECTOR_DATABASE.DROP_VECTOR_TABLE(table_name => ?); END;";

    private VecDbApiDialectLegacy() {}

    @Override
    public String createVectorTableCall() {
        return CREATE_VECTOR_TABLE;
    }

    @Override
    public void bindCreateVectorTable(CallableStatement statement, CreateVectorTableRequest request)
            throws SQLException {
        statement.setString(2, request.table().name());
        statement.setString(3, request.table().comment());
        VecDbJdbcQueryExecutor.setJson(statement, 4, request.annotationsJson());
        statement.setString(5, "dense");
        VecDbJdbcQueryExecutor.setJson(statement, 6, request.indexParametersJson());
    }

    @Override
    public String describeVectorTableCall() {
        return DESCRIBE_VECTOR_TABLE;
    }

    @Override
    public String dropVectorTableCall() {
        return DROP_VECTOR_TABLE;
    }

    @Override
    public String indexParametersToJson(
            VecDbVectorIndex vectorIndex, VecDbMetadataIndex metadataIndex, Integer parallelCreation) {
        return VecDbIndexJsonMapperLegacy.toJson(vectorIndex, metadataIndex, parallelCreation);
    }

    @Override
    public String dropMetadataIndexesJson() {
        return VecDbIndexJsonMapperLegacy.dropMetadataIndexesJson();
    }
}
