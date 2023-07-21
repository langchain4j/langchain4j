package dev.langchain4j.model.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

import static java.util.stream.Collectors.toList;

public abstract class AbstractInProcessEmbeddingModel implements EmbeddingModel {

    protected abstract OnnxEmbeddingModel model();

    @Override
    public List<Embedding> embedAll(List<TextSegment> segments) {
        return segments.stream()
                .map(segment -> {
                    float[] vector = model().embed(segment.text());
                    return Embedding.from(vector);
                })
                .collect(toList());
    }
}
