package dev.langchain4j.model.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

import static java.util.stream.Collectors.toList;

public class all_minilm_l6_v2_q_EmbeddingModel implements EmbeddingModel {

    private static final OnnxEmbeddingModel model = new OnnxEmbeddingModel(
            "/all-minilm-l6-v2-q.onnx",
            "/vocab.txt"
    );

    @Override
    public List<Embedding> embedAll(List<TextSegment> segments) {
        return segments.stream()
                .map(segment -> {
                    float[] vector = model.embed(segment.text());
                    return Embedding.from(vector);
                })
                .collect(toList());
    }
}
