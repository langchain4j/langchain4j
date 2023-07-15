package dev.langchain4j.model.embedding;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.output.Result;

import java.util.List;

public interface EmbeddingModel {

    Result<Embedding> embed(String text);

    Result<Embedding> embed(TextSegment textSegment);

    Result<List<Embedding>> embedAll(List<TextSegment> textSegments);
}