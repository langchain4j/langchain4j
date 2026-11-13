package dev.langchain4j.store.embedding.milvus.v2;

import static dev.langchain4j.store.embedding.milvus.v2.MilvusV2MetadataFilterMapper.formatValues;
import static java.lang.String.format;
import static java.util.Collections.singletonList;

import com.google.gson.JsonObject;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.filter.Filter;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.AnnSearchReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.EmbeddedText;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.request.data.SparseFloatVec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class CollectionRequestBuilder {

    static FlushReq buildFlushRequest(String collectionName) {
        return FlushReq.builder().collectionNames(singletonList(collectionName)).build();
    }

    static HasCollectionReq buildHasCollectionRequest(String collectionName) {
        return HasCollectionReq.builder().collectionName(collectionName).build();
    }

    static DropCollectionReq buildDropCollectionRequest(String collectionName) {
        return DropCollectionReq.builder().collectionName(collectionName).build();
    }

    static InsertReq buildInsertRequest(String collectionName, List<JsonObject> rows) {
        return InsertReq.builder().collectionName(collectionName).data(rows).build();
    }

    static LoadCollectionReq buildLoadCollectionInMemoryRequest(String collectionName) {
        return LoadCollectionReq.builder().collectionName(collectionName).build();
    }

    // Vector Search (Dense)
    static SearchReq buildSearchRequest(
            MilvusV2EmbeddingSearchRequest embeddingSearchRequest,
            String collectionName,
            FieldDefinition fieldDefinition,
            IndexParam.MetricType metricType,
            ConsistencyLevel consistencyLevel,
            MilvusServerVersion serverVersion) {
        return createDenseSearchReq(
                collectionName,
                embeddingSearchRequest.queryEmbedding(),
                fieldDefinition,
                metricType,
                consistencyLevel,
                embeddingSearchRequest.maxResults(),
                filterExpression(embeddingSearchRequest.filter(), fieldDefinition, serverVersion));
    }

    static HybridSearchReq buildHybridSearchRequest(
            MilvusV2EmbeddingSearchRequest embeddingSearchRequest,
            String collectionName,
            FieldDefinition fieldDefinition,
            IndexParam.MetricType metricType,
            IndexParam.MetricType sparseMetricType,
            MilvusV2Ranker ranker,
            ConsistencyLevel consistencyLevel,
            MilvusServerVersion serverVersion) {
        return createHybridSearchReq(
                fieldDefinition,
                embeddingSearchRequest.queryEmbedding(),
                embeddingSearchRequest.sparseEmbedding(),
                embeddingSearchRequest.query(),
                metricType,
                sparseMetricType,
                filterExpression(embeddingSearchRequest.filter(), fieldDefinition, serverVersion),
                embeddingSearchRequest.maxResults(),
                collectionName,
                ranker,
                consistencyLevel);
    }

    static SearchReq createDenseSearchReq(
            String collectionName,
            Embedding denseEmbedding,
            FieldDefinition fieldDefinition,
            IndexParam.MetricType metricType,
            ConsistencyLevel consistencyLevel,
            int maxResults,
            String filterExpression) {
        SearchReq.SearchReqBuilder builder = SearchReq.builder()
                .collectionName(collectionName)
                .data(Collections.singletonList(new FloatVec(denseEmbedding.vectorAsList())))
                .annsField(fieldDefinition.getVectorFieldName())
                .metricType(metricType)
                .consistencyLevel(consistencyLevel)
                .limit(maxResults)
                .outputFields(Arrays.asList(
                        fieldDefinition.getIdFieldName(),
                        fieldDefinition.getTextFieldName(),
                        fieldDefinition.getMetadataFieldName()));

        if (filterExpression != null) {
            builder.filter(filterExpression);
        }
        return builder.build();
    }

    static AnnSearchReq createDenseAnnSearchReq(
            FieldDefinition fieldDefinition,
            Embedding queryEmbedding,
            IndexParam.MetricType metricType,
            String filterExpression,
            int maxResults) {
        AnnSearchReq.AnnSearchReqBuilder builder = AnnSearchReq.builder()
                .vectorFieldName(fieldDefinition.getVectorFieldName())
                .vectors(Collections.singletonList(new FloatVec(queryEmbedding.vectorAsList())))
                .metricType(metricType)
                .limit(maxResults);

        if (filterExpression != null) {
            builder.filter(filterExpression);
        }
        return builder.build();
    }

    // This is the method for hybrid search with pre-computed sparse embedding
    static AnnSearchReq createSparseAnnSearchReq(
            FieldDefinition fieldDefinition,
            SparseEmbedding sparseEmbedding,
            IndexParam.MetricType metricType,
            String filterExpression,
            int maxResults) {
        AnnSearchReq.AnnSearchReqBuilder builder = AnnSearchReq.builder()
                .vectorFieldName(fieldDefinition.getSparseVectorFieldName())
                .vectors(Collections.singletonList(new SparseFloatVec(sparseEmbedding.vectorAsSortedMap())))
                .metricType(metricType)
                .limit(maxResults);

        if (filterExpression != null) {
            builder.filter(filterExpression);
        }
        return builder.build();
    }

    // This is the method for hybrid search with a query string - Milvus auto-computed sparse embedding (BM25)
    static AnnSearchReq createSparseAnnSearchReq(
            FieldDefinition fieldDefinition,
            String queryText,
            IndexParam.MetricType metricType,
            String filterExpression,
            int maxResults) {
        if (metricType != IndexParam.MetricType.BM25) {
            throw new IllegalArgumentException(
                    "When using plain text to query sparse embedding, metricType must be BM25 (Milvus built-in).");
        }
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("queryText must not be null or empty");
        }
        // Milvus auto-generated sparse embedding (BM25)
        AnnSearchReq.AnnSearchReqBuilder builder = AnnSearchReq.builder()
                .vectorFieldName(fieldDefinition.getSparseVectorFieldName())
                .vectors(Collections.singletonList(new EmbeddedText(queryText)))
                .metricType(metricType)
                .limit(maxResults);

        if (filterExpression != null) {
            builder.filter(filterExpression);
        }
        return builder.build();
    }

    static HybridSearchReq createHybridSearchReq(
            FieldDefinition fieldDefinition,
            Embedding denseEmbedding,
            SparseEmbedding sparseEmbedding,
            String queryText,
            IndexParam.MetricType metricType,
            IndexParam.MetricType sparseMetricType,
            String filterExpression,
            int maxResults,
            String collectionName,
            MilvusV2Ranker ranker,
            ConsistencyLevel consistencyLevel) {
        List<AnnSearchReq> searchRequests = new ArrayList<>();
        searchRequests.add(createDenseAnnSearchReq(fieldDefinition, denseEmbedding, metricType, filterExpression, maxResults));
        if (sparseEmbedding != null) {
            searchRequests.add(
                    createSparseAnnSearchReq(fieldDefinition, sparseEmbedding, sparseMetricType, filterExpression, maxResults));
        } else {
            // Milvus auto-generated sparse embedding (BM25)
            searchRequests.add(
                    createSparseAnnSearchReq(fieldDefinition, queryText, sparseMetricType, filterExpression, maxResults));
        }
        return HybridSearchReq.builder()
                .collectionName(collectionName)
                .searchRequests(searchRequests)
                .ranker(ranker.toFunction())
                .limit(maxResults)
                .outFields(Arrays.asList(
                        fieldDefinition.getIdFieldName(),
                        fieldDefinition.getTextFieldName(),
                        fieldDefinition.getMetadataFieldName()))
                .consistencyLevel(consistencyLevel)
                .build();
    }

    private static String filterExpression(Filter filter, FieldDefinition fieldDefinition, MilvusServerVersion serverVersion) {
        return filter == null
                ? null
                : MilvusV2MetadataFilterMapper.map(filter, fieldDefinition.getMetadataFieldName(), serverVersion);
    }

    static QueryReq buildQueryRequest(
            String collectionName,
            FieldDefinition fieldDefinition,
            List<String> rowIds,
            ConsistencyLevel consistencyLevel) {
        return QueryReq.builder()
                .collectionName(collectionName)
                .filter(buildQueryExpression(rowIds, fieldDefinition.getIdFieldName()))
                .consistencyLevel(consistencyLevel)
                .outputFields(singletonList(fieldDefinition.getVectorFieldName()))
                .limit(rowIds.size())
                .build();
    }

    static DeleteReq buildDeleteRequest(String collectionName, String expr) {
        return DeleteReq.builder().collectionName(collectionName).filter(expr).build();
    }

    private static String buildQueryExpression(List<String> rowIds, String idFieldName) {
        return format("%s in %s", idFieldName, formatValues(rowIds));
    }
}
