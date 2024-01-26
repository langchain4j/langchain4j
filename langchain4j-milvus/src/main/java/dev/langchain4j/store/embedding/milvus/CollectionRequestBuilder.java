package dev.langchain4j.store.embedding.milvus;

import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.MetricType;
import io.milvus.param.collection.FlushParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.partition.HasPartitionParam;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

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

    static HasPartitionParam buildHasPartitionsRequest(String collectionName, String partitionName) {
        return HasPartitionParam.newBuilder()
                .withCollectionName(collectionName)
                .withPartitionName(partitionName)
                .build();
    }

    static InsertParam buildInsertRequest(String collectionName, String partitionName, List<InsertParam.Field> fields) {
        InsertParam.Builder builder = InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(fields);
        if (StringUtils.isNotEmpty(partitionName)) {
            builder.withPartitionName(partitionName);
        }
        return builder.build();
    }

    static LoadCollectionParam buildLoadCollectionInMemoryRequest(String collectionName) {
        return LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build();
    }

    static SearchParam buildSearchRequest(String collectionName,
                                          List<Float> vector,
                                          int maxResults,
                                          MetricType metricType,
                                          ConsistencyLevelEnum consistencyLevel) {
        return buildSearchRequest(collectionName, Collections.emptyList(), vector, maxResults, metricType, consistencyLevel);
    }

    static SearchParam buildSearchRequest(String collectionName,
                                          List<String> partitionNames,
                                          List<Float> vector,
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
                .withOutFields(asList(ID_FIELD_NAME, TEXT_FIELD_NAME));
        if (Objects.nonNull(partitionNames) && !partitionNames.isEmpty()) {
            builder.withPartitionNames(partitionNames);
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

    private static String buildQueryExpression(List<String> rowIds) {
        return rowIds.stream()
                .map(id -> format("%s == '%s'", ID_FIELD_NAME, id))
                .collect(joining(" || "));
    }
}
