package dev.langchain4j.model.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Result;

import java.util.List;

import static java.util.Collections.singletonList;

/**
 * Represents a model that can convert a given text into an embedding (vector representation of the text).
 */
public interface EmbeddingModel {

    default Result<Embedding> embed(String text) {
        return embed(TextSegment.from(text));
    }

    default Result<Embedding> embed(TextSegment textSegment) {
        Result<List<Embedding>> result = embedAll(singletonList(textSegment));
        return Result.from(
                result.get().get(0),
                result.tokenUsage(),
                result.finishReason()
        );
    }

    Result<List<Embedding>> embedAll(List<TextSegment> textSegments);
}