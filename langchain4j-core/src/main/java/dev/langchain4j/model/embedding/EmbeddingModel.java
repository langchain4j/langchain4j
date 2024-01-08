package dev.langchain4j.model.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.model.output.Response;

import java.util.List;

import static java.util.Collections.singletonList;

/**
 * Represents a model that can convert a given text into an embedding (vector representation of the text).
 */
public interface EmbeddingModel {

    /**
     * Embed a text.
     *
     * @param text the text to embed.
     * @return the embedding.
     */
    default Response<Embedding> embed(String text) {
        return embed(TextSegment.from(text));
    }

    /**
     * Embed the text content of a TextSegment.
     *
     * @param textSegment the text segment to embed.
     * @return the embedding.
     */
    default Response<Embedding> embed(TextSegment textSegment) {
        Response<List<Embedding>> response = embedAll(singletonList(textSegment));
        ValidationUtils.ensureEq(response.content().size(), 1,
                "Expected a single embedding, but got %d", response.content().size());
        return Response.from(response.content().get(0), response.tokenUsage(), response.finishReason());
    }

    /**
     * Embeds the text content of a list of TextSegments.
     *
     * @param textSegments the text segments to embed.
     * @return the embeddings.
     */
    Response<List<Embedding>> embedAll(List<TextSegment> textSegments);
}