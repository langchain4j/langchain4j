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

    /**
     * Returns a list with the same size as {@code mappedSegments}, where every element is the original
     * {@code segment}.
     * <p>
     * This is useful when storing the original segment once for every embedding produced from it.
     *
     * @param segment        the original segment.
     * @param mappedSegments the mapped segments produced for the original segment.
     * @return a list containing the original segment once per mapped segment.
     */
    default List<TextSegment> originalSegmentList(TextSegment segment, List<TextSegment> mappedSegments) {
        ensureNotNull(segment, "segment");
        if (mappedSegments == null || mappedSegments.isEmpty()) {
            return List.of();
        }
        return Collections.nCopies(mappedSegments.size(), segment);
    }

    /**
     * Returns a list with the same shape as {@code mappedSegments}, where each inner list contains the
     * corresponding original segment repeated once per mapped segment.
     * <p>
     * The two input lists must have the same size and be in the same order.
     *
     * @param segments       the original segments.
     * @param mappedSegments the mapped segments produced for each original segment.
     * @return a list containing the original segments, shaped like {@code mappedSegments}.
     */
    default List<List<TextSegment>> originalSegmentList(
            List<TextSegment> segments, List<List<TextSegment>> mappedSegments) {
        ensureNotNull(segments, "segments");
        ensureNotNull(mappedSegments, "mappedSegments");
        if (segments.size() != mappedSegments.size()) {
            throw new IllegalArgumentException(
                    "The number of segments must match the number of mapped segment lists. Expected " + segments.size()
                            + " but got " + mappedSegments.size() + ".");
        }

        List<List<TextSegment>> result = new ArrayList<>(segments.size());
        for (int i = 0; i < segments.size(); i++) {
            result.add(originalSegmentList(segments.get(i), mappedSegments.get(i)));
        }
        return result;
    }
}
