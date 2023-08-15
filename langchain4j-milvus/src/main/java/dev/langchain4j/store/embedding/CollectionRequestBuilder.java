package dev.langchain4j.store.embedding;

import dev.langchain4j.store.embedding.milvus.MilvusCollectionDescription;
import dev.langchain4j.store.embedding.milvus.MilvusOperationsParams;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.MetricType;
import io.milvus.param.collection.FlushParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

class CollectionRequestBuilder {

    public static FlushParam buildFlushRequest(String collectionName) {
        return FlushParam.newBuilder()
                .withCollectionNames(Collections.singletonList(collectionName))
                .build();
    }

    public static InsertParam buildInsertRequest(List<InsertParam.Field> fields, String collectionName) {
        return InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(fields)
                .build();
    }

    public static LoadCollectionParam buildLoadCollectionInMemoryRequest(String collectionName) {
        return LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build();
    }

    public static SearchParam buildSearchRequest(List<Float> vector,
                                                 int maxResults,
                                                 MilvusCollectionDescription collectionDescription,
                                                 MilvusOperationsParams operationsParams) {
        List<String> searchOutputFields = Arrays.asList(collectionDescription.idFieldName(), collectionDescription.scalarFieldName());
        List<List<Float>> searchVector = Collections.singletonList(vector);

        return SearchParam.newBuilder()
                .withCollectionName(collectionDescription.collectionName())
                .withConsistencyLevel(ConsistencyLevelEnum.valueOf(operationsParams.consistencyLevel().name()))
                .withMetricType(MetricType.valueOf(operationsParams.metricType().name()))
                .withOutFields(searchOutputFields)
                .withTopK(maxResults)
                .withVectors(searchVector)
                .withVectorFieldName(collectionDescription.vectorFieldName())
                .build();
    }

    public static QueryParam buildQueryRequest(List<String> rowIds, MilvusCollectionDescription collectionDescription, String consistencyLevel) {
        long limit = rowIds.size();
        List<String> queryOutputFields = Collections.singletonList(collectionDescription.vectorFieldName());
        return QueryParam.newBuilder()
                .withCollectionName(collectionDescription.collectionName())
                .withConsistencyLevel(ConsistencyLevelEnum.valueOf(consistencyLevel))
                .withExpr(buildQueryExpression(rowIds, collectionDescription.idFieldName()))
                .withOutFields(queryOutputFields)
                .withOffset(0L)
                .withLimit(limit)
                .build();
    }

    private static String buildQueryExpression(List<String> rowIds, String idFieldName) {
        return rowIds.stream()
                .map(id -> String.format("%s == '%s'", idFieldName, id))
                .collect(Collectors.joining(" || "));
    }

}
