package dev.langchain4j.store.embedding.milvus;

import static dev.langchain4j.store.embedding.milvus.CollectionRequestBuilder.*;
import static io.milvus.grpc.DataType.*;
import static java.lang.String.format;

import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.FlushResponse;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.QueryResults;
import io.milvus.grpc.SearchResults;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.*;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import java.util.List;

class CollectionOperationsExecutor {

    static void flush(MilvusServiceClient milvusClient, String collectionName) {
        FlushParam request = buildFlushRequest(collectionName);
        R<FlushResponse> response = milvusClient.flush(request);
        checkResponseNotFailed(response);
    }

    static boolean hasCollection(MilvusServiceClient milvusClient, String collectionName) {
        HasCollectionParam request = buildHasCollectionRequest(collectionName);
        R<Boolean> response = milvusClient.hasCollection(request);
        checkResponseNotFailed(response);
        return response.getData();
    }

    static void createCollection(
            MilvusServiceClient milvusClient, String collectionName, FieldDefinition fieldDefinition, int dimension) {

        CreateCollectionParam request = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withSchema(CollectionSchemaParam.newBuilder()
                        .addFieldType(FieldType.newBuilder()
                                .withName(fieldDefinition.getIdFieldName())
                                .withDataType(VarChar)
                                .withMaxLength(36)
                                .withPrimaryKey(true)
                                .withAutoID(false)
                                .build())
                        .addFieldType(FieldType.newBuilder()
                                .withName(fieldDefinition.getTextFieldName())
                                .withDataType(VarChar)
                                .withMaxLength(65535)
                                .build())
                        .addFieldType(FieldType.newBuilder()
                                .withName(fieldDefinition.getMetadataFieldName())
                                .withDataType(JSON)
                                .build())
                        .addFieldType(FieldType.newBuilder()
                                .withName(fieldDefinition.getVectorFieldName())
                                .withDataType(FloatVector)
                                .withDimension(dimension)
                                .build())
                        .build())
                .build();

        R<RpcStatus> response = milvusClient.createCollection(request);
        checkResponseNotFailed(response);
    }

    static void dropCollection(MilvusServiceClient milvusClient, String collectionName) {
        DropCollectionParam request = buildDropCollectionRequest(collectionName);
        R<RpcStatus> response = milvusClient.dropCollection(request);
        checkResponseNotFailed(response);
    }

    static void createIndex(
            MilvusServiceClient milvusClient,
            String collectionName,
            String vectorFieldName,
            IndexType indexType,
            MetricType metricType,
            String extraParameters) {

        CreateIndexParam request = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName(vectorFieldName)
                .withIndexType(indexType)
                .withMetricType(metricType)
                .withExtraParam(extraParameters)
                .build();

        R<RpcStatus> response = milvusClient.createIndex(request);
        checkResponseNotFailed(response);
    }

    static void createIndex(
            MilvusServiceClient milvusClient,
            String collectionName,
            String vectorFieldName,
            IndexType indexType,
            MetricType metricType) {

        CreateIndexParam request = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName(vectorFieldName)
                .withIndexType(indexType)
                .withMetricType(metricType)
                .build();

        R<RpcStatus> response = milvusClient.createIndex(request);
        checkResponseNotFailed(response);
    }

    static void insert(MilvusServiceClient milvusClient, String collectionName, List<InsertParam.Field> fields) {
        InsertParam request = buildInsertRequest(collectionName, fields);
        R<MutationResult> response = milvusClient.insert(request);
        checkResponseNotFailed(response);
    }

    static void loadCollectionInMemory(MilvusServiceClient milvusClient, String collectionName) {
        LoadCollectionParam request = buildLoadCollectionInMemoryRequest(collectionName);
        R<RpcStatus> response = milvusClient.loadCollection(request);
        checkResponseNotFailed(response);
    }

    static SearchResultsWrapper search(MilvusServiceClient milvusClient, SearchParam searchRequest) {
        R<SearchResults> response = milvusClient.search(searchRequest);
        checkResponseNotFailed(response);

        return new SearchResultsWrapper(response.getData().getResults());
    }

    static QueryResultsWrapper queryForVectors(
            MilvusServiceClient milvusClient,
            String collectionName,
            FieldDefinition fieldDefinition,
            List<String> rowIds,
            ConsistencyLevelEnum consistencyLevel) {
        QueryParam request = buildQueryRequest(collectionName, fieldDefinition, rowIds, consistencyLevel);
        R<QueryResults> response = milvusClient.query(request);
        checkResponseNotFailed(response);

        return new QueryResultsWrapper(response.getData());
    }

    static void removeForVector(MilvusServiceClient milvusClient, String collectionName, String expr) {
        R<MutationResult> response = milvusClient.delete(buildDeleteRequest(collectionName, expr));
        checkResponseNotFailed(response);
    }

    private static <T> void checkResponseNotFailed(R<T> response) {
        if (response == null) {
            throw new RequestToMilvusFailedException("Request to Milvus DB failed. Response is null");
        } else if (response.getStatus() != R.Status.Success.getCode()) {
            String message = format("Request to Milvus DB failed. Response status:'%d'.%n", response.getStatus());
            throw new RequestToMilvusFailedException(message, response.getException());
        }
    }
}
