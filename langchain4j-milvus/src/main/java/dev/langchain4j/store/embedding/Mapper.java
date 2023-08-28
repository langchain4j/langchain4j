package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.milvus.MilvusCollectionDescription;
import dev.langchain4j.store.embedding.milvus.MilvusOperationsParams;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.MetricType;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.CollectionOperationsExecutor.queryForVectors;
import static dev.langchain4j.store.embedding.Generator.generateEmptyScalars;

class Mapper {

    public static List<List<Float>> toVectors(List<Embedding> embeddings) {
        return embeddings.stream()
                .map(Embedding::vectorAsList)
                .collect(Collectors.toList());
    }

    public static List<String> toScalars(List<TextSegment> textSegments, int size) {
        boolean noScalars = textSegments == null || textSegments.isEmpty();

        return noScalars ? generateEmptyScalars(size) : textSegmentsToScalars(textSegments);
    }


    public static List<String> textSegmentsToScalars(List<TextSegment> textSegments) {
        return textSegments.stream()
                .map(TextSegment::text)
                .collect(Collectors.toList());
    }

    public static List<EmbeddingMatch<TextSegment>> toEmbeddingMatches(MilvusServiceClient milvusClient,
                                                                       SearchResultsWrapper resultsWrapper,
                                                                       MilvusCollectionDescription collectionDescription,
                                                                       MilvusOperationsParams operationsParams,
                                                                       double minSimilarity) {
        List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();
        List<String> rowIds = (List<String>) resultsWrapper.getFieldWrapper(collectionDescription.idFieldName()).getFieldData();
        Map<String, List<Float>> idsAndVectors = getVectors(milvusClient, collectionDescription, rowIds, operationsParams);


        for (int i = 0; i < resultsWrapper.getRowRecords().size(); i++) {
            String rowId = resultsWrapper.getIDScore(0).get(i).getStrID();
            double score = resultsWrapper.getIDScore(0).get(i).getScore();
            String text = String.valueOf(resultsWrapper.getFieldData(collectionDescription.scalarFieldName(), 0).get(i));
            Embedding embedding = Embedding.from(idsAndVectors.getOrDefault(rowId, Collections.emptyList()));
            TextSegment textSegment = TextSegment.from(text);
            EmbeddingMatch<TextSegment> embeddingMatch = new EmbeddingMatch<>(score, rowId, embedding, textSegment);
            result.add(embeddingMatch);
        }

        return filterByMinSimilarity(result, minSimilarity, operationsParams.metricType().name());
    }

    private static List<EmbeddingMatch<TextSegment>> filterByMinSimilarity(List<EmbeddingMatch<TextSegment>> matches,
                                                                           double minSimilarity,
                                                                           String metricType) {

        Predicate<EmbeddingMatch<TextSegment>> predicate = getPredicate(metricType, minSimilarity);

        return matches.stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    private static Predicate<EmbeddingMatch<TextSegment>> getPredicate(String metricType, double minSimilarity) {
        Predicate<EmbeddingMatch<TextSegment>> l2Predicate = (em) -> em.score() <= minSimilarity;
        Predicate<EmbeddingMatch<TextSegment>> ipPredicate = (em) -> em.score() >= minSimilarity;

        if (MetricType.L2.equals(MetricType.valueOf(metricType))) {
            return l2Predicate;
        } else if (MetricType.IP.equals(MetricType.valueOf(metricType))) {
            return ipPredicate;
        } else {
            throw new IllegalArgumentException(String.format("Unsupported metricType: '%s'.%n", metricType));
        }
    }


    private static Map<String, List<Float>> getVectors(MilvusServiceClient milvusClient,
                                                       MilvusCollectionDescription collectionDescription,
                                                       List<String> rowIds,
                                                       MilvusOperationsParams operationsParams) {

        if (operationsParams.queryForVectorOnSearch()) {
            QueryResultsWrapper queryResultsWrapper = queryForVectors(milvusClient,
                    collectionDescription,
                    rowIds,
                    operationsParams.consistencyLevel().name());

            Map<String, List<Float>> idsAndVectors = new HashMap<>();
            for (QueryResultsWrapper.RowRecord row : queryResultsWrapper.getRowRecords()) {
                String id = row.get(collectionDescription.idFieldName()).toString();
                List<Float> vector = (List<Float>) row.get(collectionDescription.vectorFieldName());
                idsAndVectors.put(id, vector);
            }

            return idsAndVectors;
        } else {
            return Collections.emptyMap();
        }
    }

}
