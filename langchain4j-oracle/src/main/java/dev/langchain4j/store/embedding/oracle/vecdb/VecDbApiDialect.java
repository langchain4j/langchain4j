package dev.langchain4j.store.embedding.oracle.vecdb;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.store.embedding.oracle.vecdb.enums.VecDbApiVersion;
import java.sql.CallableStatement;
import java.sql.SQLException;

/** Version-specific {@code DBMS_VECTOR_DATABASE} signatures and JDBC parameter bindings. */
interface VecDbApiDialect {

    default void validateSchemaConfiguration(
            VecDbVectorIndex vectorIndex, VecDbMetadataIndex metadataIndex, Integer parallelCreation) {
        indexParametersToJson(vectorIndex, metadataIndex, parallelCreation);
    }

    String createVectorTableCall();

    void bindCreateVectorTable(CallableStatement statement, CreateVectorTableRequest request) throws SQLException;

    String describeVectorTableCall();

    String dropVectorTableCall();

    String metadataColumn();

    String indexParametersToJson(
            VecDbVectorIndex vectorIndex, VecDbMetadataIndex metadataIndex, Integer parallelCreation);

    String dropMetadataIndexesJson();

    static VecDbApiDialect forVersion(VecDbApiVersion apiVersion) {
        return switch (ensureNotNull(apiVersion, "apiVersion")) {
            case V23_26_1 -> VecDbApiDialectLegacy.INSTANCE;
            case V23_26_3 -> VecDbApiDialectNew.INSTANCE;
        };
    }

    record CreateVectorTableRequest(
            VecDbEmbeddingTable table, String annotationsJson, String tableParametersJson, String indexParametersJson) {

        public CreateVectorTableRequest {
            ensureNotNull(table, "table");
        }
    }
}
