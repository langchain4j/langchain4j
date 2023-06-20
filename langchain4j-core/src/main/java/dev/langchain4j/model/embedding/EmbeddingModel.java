package dev.langchain4j.model.embedding;

import dev.langchain4j.data.document.DocumentSegment;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.output.Result;

import java.util.List;

public interface EmbeddingModel {

    Result<Embedding> embed(String text);

    Result<Embedding> embed(DocumentSegment documentSegment);

    Result<List<Embedding>> embedAll(List<DocumentSegment> documentSegments);
}