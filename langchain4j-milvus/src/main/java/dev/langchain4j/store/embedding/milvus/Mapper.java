package dev.langchain4j.store.embedding.milvus;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.RelevanceScore;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.store.embedding.milvus.CollectionOperationsExecutor.queryForVectors;
import static dev.langchain4j.store.embedding.milvus.Generator.generateEmptyScalars;
import static dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore.*;
import static java.util.stream.Collectors.toList;

class Mapper {

    static List<List<Float>> toVectors(List<Embedding> embeddings) {
        return embeddings.stream()
                .map(Embedding::vectorAsList)
                .collect(toList());
    }

    static List<String> toScalars(List<TextSegment> textSegments, int size) {
        boolean noScalars = textSegments == null || textSegments.isEmpty();

        return noScalars ? generateEmptyScalars(size) : textSegmentsToScalars(textSegments);
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

        List<String> rowIds = (List<String>) resultsWrapper.getFieldWrapper(ID_FIELD_NAME).getFieldData();
        Map<String, Embedding> idToEmbedding = new HashMap<>();
        if (queryForVectorOnSearch) {
            idToEmbedding.putAll(queryEmbeddings(milvusClient, collectionName, rowIds, consistencyLevel));
        }

        for (int i = 0; i < resultsWrapper.getRowRecords().size(); i++) {
            double score = resultsWrapper.getIDScore(0).get(i).getScore();
            String rowId = resultsWrapper.getIDScore(0).get(i).getStrID();
            Embedding embedding = idToEmbedding.get(rowId);
            String text = String.valueOf(resultsWrapper.getFieldData(TEXT_FIELD_NAME, 0).get(i));
            TextSegment textSegment = isNullOrBlank(text) ? null : TextSegment.from(text);
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
        for (QueryResultsWrapper.RowRecord row : queryResultsWrapper.getRowRecords()) {
            String id = row.get(ID_FIELD_NAME).toString();
            List<Float> vector = (List<Float>) row.get(VECTOR_FIELD_NAME);
            idToEmbedding.put(id, Embedding.from(vector));
        }

        return idToEmbedding;
    }
}
