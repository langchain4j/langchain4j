package dev.langchain4j.store.embedding.util;

import dev.langchain4j.store.embedding.CollectionDescription;
import dev.langchain4j.store.embedding.MilvusClient;
import io.milvus.grpc.*;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.FlushParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static dev.langchain4j.store.embedding.util.CollectionRequestBuilder.*;

@Value
@Slf4j
public class CollectionOperationsExecutor {

    public static void flush(MilvusClient milvusClient, String collectionName) {
        FlushParam request = buildFlushRequest(collectionName);
        R<FlushResponse> response = milvusClient.flush(request);
        checkIfResponseSuccessful(response);
    }

    public static void insert(MilvusClient milvusClient, List<InsertParam.Field> fields, String collectionName) {
        InsertParam request = buildInsertRequest(fields, collectionName);
        R<MutationResult> response = milvusClient.insert(request);
        checkIfResponseSuccessful(response);
    }

    public static void loadCollectionInMemory(MilvusClient milvusClient, String collectionName) {
        LoadCollectionParam request = buildLoadCollectionInMemoryRequest(collectionName);
        R<RpcStatus> response = milvusClient.loadCollection(request);
        checkIfResponseSuccessful(response);
    }

    public static SearchResultsWrapper search(MilvusClient milvusClient, SearchParam searchRequest) {
        R<SearchResults> response = milvusClient.search(searchRequest);
        checkIfResponseSuccessful(response);

        return new SearchResultsWrapper(response.getData().getResults());
    }

    public static float[] queryForVector(MilvusClient milvusClient, CollectionDescription collectionDescription, String rowId) {
        QueryParam request = buildQueryRequest(rowId, collectionDescription);
        R<QueryResults> response = milvusClient.query(request);
        checkIfResponseSuccessful(response);

        FieldData vectorField = response.getData()
                .getFieldsDataList()
                .stream()
                .filter(fd -> fd.getFieldName().equals(collectionDescription.getVectorFieldName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(String.format("Cannot extract vector with the vector field name '%s'", collectionDescription.getVectorFieldName())));

        FloatArray floatVector = vectorField.getVectors().getFloatVector();

        float[] vector = new float[floatVector.getDataList().size()];
        for (int j = 0; j < floatVector.getDataList().size(); j++) {
            vector[j] = floatVector.getDataList().get(j);
        }

        return vector;
    }


    private static <T> void checkIfResponseSuccessful(R<T> response) {
        if (response.getStatus() != ErrorCode.Success.getNumber()) {
            log.error("Request failed with the next message: {}", response.getMessage());
            throw new RuntimeException(response.getException());
        }
    }
}
