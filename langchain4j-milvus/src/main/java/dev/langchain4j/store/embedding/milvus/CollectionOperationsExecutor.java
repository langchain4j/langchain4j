package dev.langchain4j.store.embedding.milvus;

import static dev.langchain4j.store.embedding.milvus.CollectionRequestBuilder.*;

import com.google.gson.JsonObject;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.DropCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.utility.request.FlushReq;
import io.milvus.v2.service.vector.request.HybridSearchReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.QueryReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import java.util.Collections;
import java.util.List;
import java.util.Map;

class CollectionOperationsExecutor {

    static void flush(MilvusClientV2 milvusClientV2, String collectionName) {
        try {
            FlushReq request = buildFlushRequest(collectionName);
            milvusClientV2.flush(request);
        } catch (Exception ex) {
            throw new RequestToMilvusFailedException("Failed to flush collection of Milvus", ex);
        }
    }

    static boolean hasCollection(MilvusClientV2 milvusClientV2, String collectionName) {
        try {
            HasCollectionReq req = buildHasCollectionRequest(collectionName);
            return milvusClientV2.hasCollection(req);
        } catch (Exception ex) {
            throw new RequestToMilvusFailedException("Failed to check if collection exists", ex);
        }
    }

    static void createCollection(
            MilvusClientV2 milvusClientV2, String collectionName, FieldDefinition fieldDefinition, int dimension) {
        try {
            List<CreateCollectionReq.FieldSchema> fields = List.of(
                    CreateCollectionReq.FieldSchema.builder()
                            .name(fieldDefinition.getIdFieldName())
                            .dataType(DataType.VarChar)
                            .maxLength(36)
                            .isPrimaryKey(true)
                            .autoID(false)
                            .build(),
                    CreateCollectionReq.FieldSchema.builder()
                            .name(fieldDefinition.getTextFieldName())
                            .dataType(DataType.VarChar)
                            .maxLength(65535)
                            .enableAnalyzer(true)
                            .analyzerParams(Map.of("type", "standard"))
                            .enableMatch(true)
                            .build(),
                    CreateCollectionReq.FieldSchema.builder()
                            .name(fieldDefinition.getMetadataFieldName())
                            .dataType(DataType.JSON)
                            .build(),
                    CreateCollectionReq.FieldSchema.builder()
                            .name(fieldDefinition.getSparseVectorFieldName())
                            .dataType(DataType.SparseFloatVector)
                            .build(),
                    CreateCollectionReq.FieldSchema.builder()
                            .name(fieldDefinition.getVectorFieldName())
                            .dataType(DataType.FloatVector)
                            .dimension(dimension)
                            .build());

            CreateCollectionReq.CollectionSchema schema = CreateCollectionReq.CollectionSchema.builder()
                    .fieldSchemaList(fields)
                    .build();

            CreateCollectionReq req = CreateCollectionReq.builder()
                    .collectionName(collectionName)
                    .collectionSchema(schema)
                    .numShards(2)
                    .build();

            milvusClientV2.createCollection(req);
        } catch (Exception ex) {
            throw new RequestToMilvusFailedException("Failed to create collection of Milvus", ex);
        }
    }

    static void dropCollection(MilvusClientV2 milvusClientV2, String collectionName) {
        try {
            DropCollectionReq request = buildDropCollectionRequest(collectionName);
            milvusClientV2.dropCollection(request);
        } catch (Exception ex) {
            throw new RequestToMilvusFailedException("Failed to drop collection of Milvus", ex);
        }
    }

    static void createIndex(
            MilvusClientV2 milvusClientV2,
            String collectionName,
            String vectorFieldName,
            IndexParam.IndexType indexType,
            IndexParam.MetricType metricType) {
        try {
            IndexParam indexParam = IndexParam.builder()
                    .fieldName(vectorFieldName)
                    .indexType(indexType)
                    .metricType(metricType)
                    .build();

            CreateIndexReq req = CreateIndexReq.builder()
                    .collectionName(collectionName)
                    .indexParams(Collections.singletonList(indexParam))
                    .build();

            milvusClientV2.createIndex(req);
        } catch (Exception ex) {
            throw new RequestToMilvusFailedException("Failed to create index of Milvus", ex);
        }
    }

    static void insert(MilvusClientV2 milvusClientV2, String collectionName, List<JsonObject> rows) {
        try {
            InsertReq request = buildInsertRequest(collectionName, rows);
            InsertResp resp = milvusClientV2.insert(request);
            if (resp == null || resp.getInsertCnt() < rows.size()) {
                throw new RequestToMilvusFailedException(java.lang.String.format(
                        "Expected to insert %d rows, but got %d.",
                        rows.size(), resp == null ? 0L : resp.getInsertCnt()));
            }
        } catch (Exception ex) {
            throw new RequestToMilvusFailedException("Failed to insert into Milvus", ex);
        }
    }

    static void loadCollectionInMemory(MilvusClientV2 milvusClientV2, String collectionName) {
        try {
            milvusClientV2.loadCollection(buildLoadCollectionInMemoryRequest(collectionName));
        } catch (Exception ex) {
            throw new RequestToMilvusFailedException("Failed to load collection of Milvus", ex);
        }
    }

    static SearchResp search(MilvusClientV2 client, SearchReq searchReq) {
        try {
            return client.search(searchReq);
        } catch (Exception ex) {
            throw new RequestToMilvusFailedException("Failed to search Milvus", ex);
        }
    }

    // hybrid search
    static SearchResp search(MilvusClientV2 client, HybridSearchReq hybridSearchReq) {
        try {
            return client.hybridSearch(hybridSearchReq);
        } catch (Exception ex) {
            throw new RequestToMilvusFailedException("Failed to hybrid search Milvus", ex);
        }
    }

    static QueryResp queryForVectors(
            MilvusClientV2 milvusClientV2,
            String collectionName,
            FieldDefinition fieldDefinition,
            List<String> rowIds,
            ConsistencyLevel consistencyLevel) {
        try {
            QueryReq request = buildQueryRequest(collectionName, fieldDefinition, rowIds, consistencyLevel);
            return milvusClientV2.query(request);
        } catch (Exception ex) {
            throw new RequestToMilvusFailedException("Failed to query Milvus", ex);
        }
    }

    static void removeForVector(MilvusClientV2 milvusClientV2, String collectionName, String expr) {
        try {
            milvusClientV2.delete(buildDeleteRequest(collectionName, expr));
        } catch (Exception ex) {
            throw new RequestToMilvusFailedException("Failed to delete from Milvus", ex);
        }
    }
}
