package dev.langchain4j.data.embedding;

import dev.langchain4j.data.segment.TextSegment;

import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toList;

/**
 * Defines the interface for transforming a {@link Embedding}.
 * Implementations can perform a variety of tasks such as encrypting, salting, etc.
 */
public interface EmbeddingTransformer {

    /**
     * Transforms a provided embedding.
     *
     * @param embedding The embedding to be transformed.
     * @return The transformed embedding, or null if the segment should be filtered out.
     */
    Embedding transform(Embedding embedding);

    /**
     * Transforms all the provided embeddings.
     *
     * @param embeddings A list of embeddings to be transformed.
     * @return A list of transformed embeddings. The length of this list may be shorter or longer than the original list. Returns an empty list if all segments were filtered out.
     */
    default List<Embedding> transformAll(List<Embedding> embeddings) {
        return embeddings.stream()
                .map(this::transform)
                .filter(Objects::nonNull)
                .collect(toList());
    }

}
