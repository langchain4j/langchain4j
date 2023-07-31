package dev.langchain4j.store.embedding.util;

import dev.langchain4j.store.embedding.CollectionDescription;
import dev.langchain4j.store.embedding.MilvusClient;
import io.milvus.grpc.FieldData;
import io.milvus.grpc.FloatArray;
import io.milvus.grpc.QueryResults;
import io.milvus.grpc.SearchResults;
import io.milvus.param.R;
import io.milvus.param.collection.FlushParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;

import java.util.List;

import static dev.langchain4j.store.embedding.util.CollectionRequestBuilder.*;

public class CollectionOperationsExecutor {

    public static void flush(MilvusClient milvusClient, String collectionName) {
        FlushParam request = buildFlushRequest(collectionName);
        milvusClient.flush(request);
    }

    public static void insert(MilvusClient milvusClient, List<InsertParam.Field> fields, String collectionName) {
        InsertParam request = buildInsertRequest(fields, collectionName);
        milvusClient.insert(request);
    }

    public static void loadCollectionInMemory(MilvusClient milvusClient, String collectionName) {
        LoadCollectionParam request = buildLoadCollectionInMemoryRequest(collectionName);
        milvusClient.loadCollection(request);
    }

    public static SearchResultsWrapper search(MilvusClient milvusClient, SearchParam searchRequest) {
        R<SearchResults> response = milvusClient.search(searchRequest);

        return new SearchResultsWrapper(response.getData().getResults());
    }

    public static float[] queryForVector(MilvusClient milvusClient, CollectionDescription collectionDescription, String rowId) {
        QueryParam request = buildQueryRequest(rowId, collectionDescription);
        R<QueryResults> response = milvusClient.query(request);

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


}
