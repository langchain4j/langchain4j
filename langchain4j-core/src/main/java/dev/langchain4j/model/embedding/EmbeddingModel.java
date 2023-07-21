package dev.langchain4j.model.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

import static java.util.Collections.singletonList;

public interface EmbeddingModel {

    default Embedding embed(String text) {
        return embed(TextSegment.from(text));
    }

    default Embedding embed(TextSegment textSegment) {
        return embedAll(singletonList(textSegment)).get(0);
    }

    List<Embedding> embedAll(List<TextSegment> textSegments);
}