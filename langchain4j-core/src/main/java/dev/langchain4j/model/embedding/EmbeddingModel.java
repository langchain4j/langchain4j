package dev.langchain4j.model.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;

import java.util.List;

import static java.util.Collections.singletonList;

/**
 * Represents a model that can convert a given text into an embedding (vector representation of the text).
 */
public interface EmbeddingModel {

    default Response<Embedding> embed(String text) {
        return embed(TextSegment.from(text));
    }

    default Response<Embedding> embed(TextSegment textSegment) {
        Response<List<Embedding>> response = embedAll(singletonList(textSegment));
        return Response.from(
                response.content().get(0),
                response.tokenUsage(),
                response.finishReason()
        );
    }

    Response<List<Embedding>> embedAll(List<TextSegment> textSegments);
}