package dev.langchain4j.store.embedding.mongodb;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.mongodb.document.EmbeddingDocument;
import dev.langchain4j.store.embedding.mongodb.document.EmbeddingMatchDocument;
import dev.langchain4j.store.embedding.mongodb.document.TextSegmentDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class DocumentMapping {

    public EmbeddingDocument generateDocument(String id, Embedding embedding, TextSegment textSegment) {
        return new EmbeddingDocument(id, asDoublesList(embedding.vector()), asDocument(textSegment));
    }

    public EmbeddingMatch<TextSegment> asTextSegmentEmbeddingMatch(EmbeddingMatchDocument d) {
        return new EmbeddingMatch<>(d.getScore(), d.getId(), new Embedding(asFloatArray(d.getEmbedding())), asTextSegment(d.getEmbedded()));
    }

    private TextSegment asTextSegment(TextSegmentDocument document) {
        String text = document.getText();
        Map<String, String> metadata = document.getMetadata();

        return new TextSegment(text, new Metadata(metadata));
    }

    private TextSegmentDocument asDocument(TextSegment textSegment) {
        return new TextSegmentDocument(textSegment.text(), textSegment.metadata().asMap());
    }

    private List<Double> asDoublesList(float[] input) {
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < input.length; i++) {
            double d = input[i];
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
