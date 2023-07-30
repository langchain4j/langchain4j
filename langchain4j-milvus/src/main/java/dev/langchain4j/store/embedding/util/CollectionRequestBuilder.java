package dev.langchain4j.store.embedding.util;

import dev.langchain4j.store.embedding.CollectionDescription;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.MetricType;
import io.milvus.param.collection.FlushParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import lombok.Value;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Value
public class CollectionRequestBuilder {

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

    public static SearchParam buildSearchRequest(List<Float> vector, int maxResults, CollectionDescription collectionDescription) {
        List<String> searchOutputFields = Arrays.asList(collectionDescription.getIdFieldName(), collectionDescription.getScalarFieldName());
        List<List<Float>> searchVector = Collections.singletonList(vector);

        return SearchParam.newBuilder()
                .withCollectionName(collectionDescription.getCollectionName())
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .withMetricType(MetricType.L2)
                .withOutFields(searchOutputFields)
                .withTopK(maxResults)
                .withVectors(searchVector)
                .withVectorFieldName(collectionDescription.getVectorFieldName())
                .build();
    }

    public static QueryParam buildQueryRequest(String rowId, CollectionDescription collectionDescription) {
        List<String> queryOutputFields = Collections.singletonList(collectionDescription.getVectorFieldName());
        return QueryParam.newBuilder()
                .withCollectionName(collectionDescription.getCollectionName())
                .withConsistencyLevel(ConsistencyLevelEnum.STRONG)
                .withExpr(buildQueryExpression(rowId, collectionDescription.getIdFieldName()))
                .withOutFields(queryOutputFields)
                .withOffset(0L)
                .withLimit(1L)
                .build();
    }

    private static String buildQueryExpression(String rowId, String idFieldName) {
        return String.format("%s == '%s'", idFieldName, rowId);
    }

}
