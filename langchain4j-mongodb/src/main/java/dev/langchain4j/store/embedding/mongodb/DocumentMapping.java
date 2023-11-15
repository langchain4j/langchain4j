package dev.langchain4j.store.embedding.mongodb;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;

import java.util.ArrayList;
import java.util.List;


public class DocumentMapping {

    EmbeddingDocument generateDocument(String id, Embedding embedding, TextSegment textSegment) {
        if (textSegment == null) {
            return new EmbeddingDocument(id, asDoublesList(embedding.vector()));
        }
        return new EmbeddingDocument(id, asDoublesList(embedding.vector()), textSegment.text(), textSegment.metadata().asMap());
    }


    EmbeddingMatch<TextSegment> asTextSegmentEmbeddingMatch(EmbeddingMatchDocument d) {
        return new EmbeddingMatch<>(d.getScore(), d.getId(), new Embedding(asFloatArray(d.getEmbedding())), new TextSegment(d.getText(), new Metadata(d.getMetadata())));
    }

    private List<Double> asDoublesList(float[] input) {
        List<Double> result = new ArrayList<>();
        for (double d : input) {
            result.add(d);
        }
        return result;
    }

    private float[] asFloatArray(List<Double> input) {
        float[] floats = new float[input.size()];
        for (int i = 0; i < input.size(); i++) {
            floats[i] = input.get(i).floatValue();
        }
        return floats;

    }


}
