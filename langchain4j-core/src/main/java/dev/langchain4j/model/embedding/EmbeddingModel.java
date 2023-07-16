package dev.langchain4j.model.embedding;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;

import java.util.List;

public interface EmbeddingModel {

    Embedding embed(String text);

    Embedding embed(TextSegment textSegment);

    List<Embedding> embedAll(List<TextSegment> textSegments);
}