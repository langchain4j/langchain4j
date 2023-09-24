package dev.langchain4j.store.embedding.milvus;

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

    static FlushParam buildFlushRequest(String collectionName) {
        return FlushParam.newBuilder()
                .withCollectionNames(Collections.singletonList(collectionName))
                .build();
    }

    static InsertParam buildInsertRequest(List<InsertParam.Field> fields, String collectionName) {
        return InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(fields)
                .build();
    }

    static LoadCollectionParam buildLoadCollectionInMemoryRequest(String collectionName) {
        return LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build();
    }

    static SearchParam buildSearchRequest(List<Float> vector,
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

    static QueryParam buildQueryRequest(List<String> rowIds, MilvusCollectionDescription collectionDescription, String consistencyLevel) {
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
