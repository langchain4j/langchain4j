package dev.langchain4j.model.jinaAi;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.List;

public class JinaAiEmbeddingModel implements EmbeddingModel {
    @Override
    public Response<Embedding> embed(String text) {
        return EmbeddingModel.super.embed(text);
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        return EmbeddingModel.super.embed(textSegment);
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        return null;
    }
}
