package dev.langchain4j.store.embedding.oracle.vecdb;

import dev.langchain4j.store.embedding.oracle.vecdb.mapper.VecDbIndexJsonMapper;
import java.sql.CallableStatement;
import java.sql.SQLException;

/** {@code DBMS_VECTOR_DATABASE} API dialect used by Oracle Database 23.26.3 and later. */
final class VecDbApiDialectNew implements VecDbApiDialect {

    static final VecDbApiDialectNew INSTANCE = new VecDbApiDialectNew();

    private static final String CREATE_VECTOR_TABLE = "BEGIN ? := DBMS_VECTOR_DATABASE.CREATE_VECTOR_TABLE("
            + "name => ?, comment => ?, annotations => ?, table_params => ?, "
            + "embed_params => NULL, index_params => ?); END;";
    private static final String DESCRIBE_VECTOR_TABLE =
            "BEGIN ? := DBMS_VECTOR_DATABASE.DESCRIBE_VECTOR_TABLE(name => ?); END;";
    private static final String DROP_VECTOR_TABLE =
            "BEGIN ? := DBMS_VECTOR_DATABASE.DROP_VECTOR_TABLE(name => ?); END;";

    private VecDbApiDialectNew() {}

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
        VecDbJdbcQueryExecutor.setJson(statement, 5, request.tableParametersJson());
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
        return VecDbIndexJsonMapper.toJson(vectorIndex, metadataIndex, parallelCreation);
    }

    @Override
    public String dropMetadataIndexesJson() {
        return VecDbIndexJsonMapper.dropMetadataIndexesJson();
    }
}
