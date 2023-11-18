package dev.langchain4j.store.embedding.mongodb;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;


public class DocumentMapping {

    EmbeddingDocument generateDocument(String id, Embedding embedding, TextSegment textSegment) {
        if (textSegment == null) {
            return new EmbeddingDocument(id, embedding.vectorAsList());
        }
        return new EmbeddingDocument(id, embedding.vectorAsList(), textSegment.text(), textSegment.metadata().asMap());
    }

    EmbeddingMatch<TextSegment> asTextSegmentEmbeddingMatch(EmbeddingMatchDocument d) {
        TextSegment textSegment = null;
        if (d.getMetadata() != null) {
            textSegment = new TextSegment(d.getText(), new Metadata(d.getMetadata()));
        }
        return new EmbeddingMatch<>(d.getScore(), d.getId(), Embedding.from(d.getEmbedding()), textSegment);
    }
}
