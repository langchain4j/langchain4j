package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.store.embedding.filter.Filter;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.MetricType;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.FlushParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;

import java.util.List;

import static dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore.*;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;

class CollectionRequestBuilder {

    static FlushParam buildFlushRequest(String collectionName) {
        return FlushParam.newBuilder()
                .withCollectionNames(singletonList(collectionName))
                .build();
    }

    static HasCollectionParam buildHasCollectionRequest(String collectionName) {
        return HasCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build();
    }

    static DropCollectionParam buildDropCollectionRequest(String collectionName) {
        return DropCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build();
    }

    static InsertParam buildInsertRequest(String collectionName, List<InsertParam.Field> fields) {
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

    static SearchParam buildSearchRequest(String collectionName,
                                          List<Float> vector,
                                          Filter filter,
                                          int maxResults,
                                          MetricType metricType,
                                          ConsistencyLevelEnum consistencyLevel) {
        SearchParam.Builder builder = SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withVectors(singletonList(vector))
                .withVectorFieldName(VECTOR_FIELD_NAME)
                .withTopK(maxResults)
                .withMetricType(metricType)
                .withConsistencyLevel(consistencyLevel)
                .withOutFields(asList(ID_FIELD_NAME, TEXT_FIELD_NAME, METADATA_FIELD_NAME));

        if (filter != null) {
            builder.withExpr(MilvusMetadataFilterMapper.map(filter));
        }

        return builder.build();
    }

    static QueryParam buildQueryRequest(String collectionName,
                                        List<String> rowIds,
                                        ConsistencyLevelEnum consistencyLevel) {
        return QueryParam.newBuilder()
                .withCollectionName(collectionName)
                .withExpr(buildQueryExpression(rowIds))
                .withConsistencyLevel(consistencyLevel)
                .withOutFields(singletonList(VECTOR_FIELD_NAME))
                .build();
    }

    static DeleteParam buildDeleteRequest(String collectionName,
                                          String expr) {
        return DeleteParam.newBuilder()
                .withCollectionName(collectionName)
                .withExpr(expr)
                .build();
    }

    private static String buildQueryExpression(List<String> rowIds) {
        return rowIds.stream()
                .map(id -> format("%s == '%s'", ID_FIELD_NAME, id))
                .collect(joining(" || "));
    }
}
