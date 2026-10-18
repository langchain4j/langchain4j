package dev.langchain4j.data.segment;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Maps an original {@link TextSegment} to zero or more {@link TextSegment}s that are used only as
 * inputs for embedding.
 * <p>
 * The original segment is stored in the embedding store, while the mapped segments are embedded.
 * This allows a single segment to be represented by multiple embeddings (for example, a summary,
 * a natural-language explanation, or hypothetical questions) without changing the content that is
 * returned during retrieval.
 * <p>
 * Implementations can be provided by LangChain4j or by users, similar to other transformation
 * interfaces such as {@link TextSegmentTransformer}.
 * <p>
 * The mapper may return an empty list or {@code null} to filter out the original segment.
 *
 * @since 1.21.0
 */
@Experimental
@FunctionalInterface
public interface EmbeddingInputMapper {

    /**
     * Maps the given segment to zero or more segments that should be embedded.
     *
     * @param segment the original segment.
     * @return the segments to embed. An empty list or {@code null} filters out the original segment.
     */
    List<TextSegment> map(TextSegment segment);

    /**
     * Maps all the given segments to embedding inputs.
     * <p>
     * The default implementation calls {@link #map(TextSegment)} for each segment. Implementations that
     * generate descriptions with an LLM can override this method to batch the generation and reduce the
     * number of model calls.
     * <p>
     * The returned list must contain exactly one entry per input segment, in the same order. Each entry
     * may be empty to filter out the corresponding segment.
     *
     * @param segments the original segments.
     * @return one list of embedding inputs per input segment, in the same order.
     */
    default List<List<TextSegment>> mapAll(List<TextSegment> segments) {
        return segments.stream()
                .map(segment -> {
                    List<TextSegment> mapped = map(segment);
                    return mapped == null ? List.<TextSegment>of() : mapped;
                })
                .toList();
    }
}
