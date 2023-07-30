package dev.langchain4j.store.embedding.util;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.CollectionDescription;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.MilvusClient;
import io.milvus.response.SearchResultsWrapper;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.util.CollectionOperationsExecutor.queryForVector;
import static dev.langchain4j.store.embedding.util.Generator.generateEmptyScalars;

@Value
public class Mapper {

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

    public static List<EmbeddingMatch<TextSegment>> toEmbeddingMatches(MilvusClient milvusClient, SearchResultsWrapper resultsWrapper, CollectionDescription collectionDescription) {
        List<EmbeddingMatch<TextSegment>> result = new ArrayList<>();

        for (int i = 0; i < resultsWrapper.getRowRecords().size(); i++) {
            String rowId = resultsWrapper.getIDScore(0).get(i).getStrID();
            double score = resultsWrapper.getIDScore(0).get(i).getScore();
            String text = String.valueOf(resultsWrapper.getFieldData(collectionDescription.getScalarFieldName(), 0).get(i));
            float[] vector = queryForVector(milvusClient, collectionDescription, rowId);

            EmbeddingMatch<TextSegment> embeddingMatch = new EmbeddingMatch<>(rowId,
                    new Embedding(vector),
                    new TextSegment(text, null),
                    score
            );

            result.add(embeddingMatch);
        }

        return result;
    }
}
