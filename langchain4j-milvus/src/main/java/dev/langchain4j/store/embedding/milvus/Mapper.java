package dev.langchain4j.store.embedding.milvus;

import static com.google.gson.ToNumberPolicy.LONG_OR_DOUBLE;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.store.embedding.milvus.CollectionOperationsExecutor.queryForVectors;
import static dev.langchain4j.store.embedding.milvus.Generator.generateEmptyJsons;
import static dev.langchain4j.store.embedding.milvus.Generator.generateEmptyScalars;
import static java.util.stream.Collectors.toList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.embedding.SparseEmbedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.RelevanceScore;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.service.vector.response.QueryResp;
import io.milvus.v2.service.vector.response.SearchResp;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

class Mapper {

    private static final Gson GSON =
            new GsonBuilder().setObjectToNumberStrategy(LONG_OR_DOUBLE).create();

    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    static List<List<Float>> toVectors(List<Embedding> embeddings) {
        return embeddings.stream().map(Embedding::vectorAsList).collect(toList());
    }

    static List<SortedMap<Long, Float>> toSparseVectors(List<SparseEmbedding> embeddings) {
        return embeddings.stream()
                .map(e -> {
                    List<Long> indices = e.getIndices();
                    List<Float> values = e.getValues();

                    SortedMap<Long, Float> map = new TreeMap<>();
                    for (int i = 0; i < indices.size(); i++) {
                        map.put(indices.get(i), values.get(i));
                    }
                    return map;
                })
                .collect(Collectors.toList());
    }

    static List<String> toScalars(List<TextSegment> textSegments, int size) {
        return isNullOrEmpty(textSegments) ? generateEmptyScalars(size) : textSegmentsToScalars(textSegments);
    }

    static List<JsonObject> toMetadataJsons(List<TextSegment> textSegments, int size) {
        return isNullOrEmpty(textSegments)
                ? generateEmptyJsons(size)
                : textSegments.stream()
                        .map(segment ->
                                GSON.toJsonTree(segment.metadata().toMap()).getAsJsonObject())
                        .collect(toList());
    }

    static List<String> textSegmentsToScalars(List<TextSegment> textSegments) {
        return textSegments.stream().map(TextSegment::text).collect(toList());
    }

    static List<EmbeddingMatch<TextSegment>> toEmbeddingMatches(
            MilvusClientV2 milvusClientV2,
            SearchResp searchResp,
            String collectionName,
            FieldDefinition fieldDefinition,
            ConsistencyLevel consistencyLevel,
            boolean queryForVectorOnSearch) {
        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();

        Map<String, Embedding> idToEmbedding = new HashMap<>();
        if (queryForVectorOnSearch
                && !searchResp.getSearchResults().isEmpty()
                && !searchResp.getSearchResults().get(0).isEmpty()) {
            try {
                List<String> rowIds = searchResp.getSearchResults().stream()
                        .flatMap(Collection::stream)
                        .map(searchResult -> {
                            if (searchResult.getId() != null) {
                                return searchResult.getId().toString();
                            }
                            return searchResult
                                    .getEntity()
                                    .get(fieldDefinition.getIdFieldName())
                                    .toString();
                        })
                        .collect(Collectors.toList());
                idToEmbedding.putAll(
                        queryEmbeddings(milvusClientV2, collectionName, fieldDefinition, rowIds, consistencyLevel));
            } catch (Exception e) {
                // There is no way to check if the result is empty or not.
                // If the result is empty, the exception will be thrown.
                throw new RuntimeException(e);
            }
        }

        for (int i = 0; i < searchResp.getSearchResults().get(0).size(); i++) {
            double score = searchResp.getSearchResults().get(0).get(i).getScore();
            String rowId = searchResp.getSearchResults().get(0).get(i).getId().toString();
            if (rowId == null) {
                rowId = searchResp
                        .getSearchResults()
                        .get(0)
                        .get(i)
                        .getEntity()
                        .get(fieldDefinition.getIdFieldName())
                        .toString();
            }
            Embedding embedding = idToEmbedding.get(rowId);
            TextSegment textSegment =
                    toTextSegment(searchResp.getSearchResults().get(0).get(i), fieldDefinition);
            EmbeddingMatch<TextSegment> embeddingMatch =
                    new EmbeddingMatch<>(RelevanceScore.fromCosineSimilarity(score), rowId, embedding, textSegment);
            matches.add(embeddingMatch);
        }

        return matches;
    }

    private static TextSegment toTextSegment(SearchResp.SearchResult searchResult, FieldDefinition fieldDefinition) {
        Object textField = searchResult.getEntity().get(fieldDefinition.getTextFieldName());
        String text = textField == null ? null : textField.toString();
        if (isNullOrBlank(text)) {
            return null;
        }

        if (!searchResult.getEntity().containsKey(fieldDefinition.getMetadataFieldName())) {
            return TextSegment.from(text);
        }

        JsonObject metadata = (JsonObject) searchResult.getEntity().get(fieldDefinition.getMetadataFieldName());
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

    private static Map<String, Embedding> queryEmbeddings(
            MilvusClientV2 milvusClientV2,
            String collectionName,
            FieldDefinition fieldDefinition,
            List<String> rowIds,
            ConsistencyLevel consistencyLevel) {
        QueryResp queryResults =
                queryForVectors(milvusClientV2, collectionName, fieldDefinition, rowIds, consistencyLevel);

        Map<String, Embedding> idToEmbedding = new HashMap<>();
        for (QueryResp.QueryResult result : queryResults.getQueryResults()) {
            Map<String, Object> row = result.getEntity();
            String id = row.get(fieldDefinition.getIdFieldName()).toString();
            List<Float> vector = (List<Float>) row.get(fieldDefinition.getVectorFieldName());
            idToEmbedding.put(id, Embedding.from(vector));
        }

        return idToEmbedding;
    }
}
