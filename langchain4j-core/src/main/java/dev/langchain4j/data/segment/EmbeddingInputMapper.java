package dev.langchain4j.data.segment;

import dev.langchain4j.Experimental;
import java.util.List;
import java.util.Objects;

/**
 * Maps an original {@link TextSegment} to zero or more {@link MappedSegment}s, each carrying an
 * embedding input and the content to store in the embedding store.
 * <p>
 * This decouples what is embedded from what is stored and returned by retrieval. A single segment can
 * be represented by multiple embeddings (for example, a summary, a natural-language explanation, or
 * hypothetical questions) while the original content is still returned to the LLM.
 * <p>
 * The stored content does not have to be the original segment: it can also be the generated
 * description, so retrieval returns the description instead of the original text.
 * <p>
 * Implementations can be provided by LangChain4j or by users, similar to other transformation
 * interfaces such as {@link TextSegmentTransformer}.
 * <p>
 * The mapper may return an empty list or {@code null} to filter out the original segment.
 *
 * @since 1.23.0
 */
@Experimental
public interface EmbeddingInputMapper {

    /**
     * Maps the given segment to zero or more embedding inputs.
     *
     * @param segment the original segment.
     * @return the mapped segments. An empty list or {@code null} filters out the original segment.
     */
    List<MappedSegment> map(TextSegment segment);

    /**
     * Maps all the given segments.
     * <p>
     * The default implementation calls {@link #map(TextSegment)} for each segment. Implementations that
     * generate descriptions with an LLM can override this method to batch the generation and reduce the
     * number of model calls.
     * <p>
     * The returned list may be shorter or longer than the input list. Return an empty list to filter out
     * all segments.
     *
     * @param segments the original segments.
     * @return the mapped segments, in the order they should be embedded.
     */
    default List<MappedSegment> mapAll(List<TextSegment> segments) {
        return segments.stream()
                .map(this::map)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .toList();
    }
}
