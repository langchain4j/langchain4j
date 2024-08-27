package dev.langchain4j.store.embedding.milvus;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.RelevanceScore;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.exception.ParamException;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.QueryResultsWrapper.RowRecord;
import io.milvus.response.SearchResultsWrapper;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.store.embedding.milvus.CollectionOperationsExecutor.queryForVectors;
import static dev.langchain4j.store.embedding.milvus.Generator.generateEmptyJsons;
import static dev.langchain4j.store.embedding.milvus.Generator.generateEmptyScalars;
import static dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore.*;
import static java.util.stream.Collectors.toList;

class Mapper {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {
    }.getType();

    static List<List<Float>> toVectors(List<Embedding> embeddings) {
        return embeddings.stream()
                .map(Embedding::vectorAsList)
                .collect(toList());
    }

    static List<String> toScalars(List<TextSegment> textSegments, int size) {
        return isNullOrEmpty(textSegments) ? generateEmptyScalars(size) : textSegmentsToScalars(textSegments);
    }

    static List<JsonObject> toMetadataJsons(List<TextSegment> textSegments, int size) {
        return isNullOrEmpty(textSegments) ? generateEmptyJsons(size) : textSegments.stream()
                .map(segment -> GSON.toJsonTree(segment.metadata().toMap()).getAsJsonObject())
                .collect(toList());
    }

    static List<String> textSegmentsToScalars(List<TextSegment> textSegments) {
        return textSegments.stream()
                .map(TextSegment::text)
                .collect(toList());
    }

    static List<EmbeddingMatch<TextSegment>> toEmbeddingMatches(MilvusServiceClient milvusClient,
                                                                SearchResultsWrapper resultsWrapper,
                                                                String collectionName,
                                                                ConsistencyLevelEnum consistencyLevel,
                                                                boolean queryForVectorOnSearch) {
        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();

        Map<String, Embedding> idToEmbedding = new HashMap<>();
        if (queryForVectorOnSearch) {
            try {
                List<String> rowIds = (List<String>) resultsWrapper.getFieldWrapper(ID_FIELD_NAME).getFieldData();
                idToEmbedding.putAll(queryEmbeddings(milvusClient, collectionName, rowIds, consistencyLevel));
            } catch (ParamException e) {
                // There is no way to check if the result is empty or not.
                // If the result is empty, the exception will be thrown.
            }
        }

        for (int i = 0; i < resultsWrapper.getRowRecords().size(); i++) {
            double score = resultsWrapper.getIDScore(0).get(i).getScore();
            String rowId = resultsWrapper.getIDScore(0).get(i).getStrID();
            Embedding embedding = idToEmbedding.get(rowId);
            TextSegment textSegment = toTextSegment(resultsWrapper.getRowRecords().get(i));
            EmbeddingMatch<TextSegment> embeddingMatch = new EmbeddingMatch<>(
                    RelevanceScore.fromCosineSimilarity(score),
                    rowId,
                    embedding,
                    textSegment
            );
            matches.add(embeddingMatch);
        }

        return matches;
    }

    private static TextSegment toTextSegment(RowRecord rowRecord) {

        String text = (String) rowRecord.get(TEXT_FIELD_NAME);
        if (isNullOrBlank(text)) {
            return null;
        }

        if (!rowRecord.getFieldValues().containsKey(METADATA_FIELD_NAME)) {
            return TextSegment.from(text);
        }

        JsonObject metadata = (JsonObject) rowRecord.get(METADATA_FIELD_NAME);
        return TextSegment.from(text, toMetadata(metadata));
    }

    private static Metadata toMetadata(JsonObject metadata) {
        Map<String, Object> metadataMap = GSON.fromJson(metadata, MAP_TYPE);
        metadataMap.forEach((key, value) -> {
            if (value instanceof BigDecimal) {
                // It is safe to convert. No information is lost, the "biggest" type allowed in Metadata is double.
                metadataMap.put(key, ((BigDecimal) value).doubleValue());
            }
        });
        return Metadata.from(metadataMap);
    }

    private static Map<String, Embedding> queryEmbeddings(MilvusServiceClient milvusClient,
                                                          String collectionName,
                                                          List<String> rowIds,
                                                          ConsistencyLevelEnum consistencyLevel) {
        QueryResultsWrapper queryResultsWrapper = queryForVectors(
                milvusClient,
                collectionName,
                rowIds,
                consistencyLevel
        );

        Map<String, Embedding> idToEmbedding = new HashMap<>();
        for (RowRecord row : queryResultsWrapper.getRowRecords()) {
            String id = row.get(ID_FIELD_NAME).toString();
            List<Float> vector = (List<Float>) row.get(VECTOR_FIELD_NAME);
            idToEmbedding.put(id, Embedding.from(vector));
        }

        return idToEmbedding;
    }
}