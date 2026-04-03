package dev.langchain4j.data.segment;

import static java.util.stream.Collectors.toList;

import dev.langchain4j.internal.Utils;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Defines the interface for transforming a {@link TextSegment}.
 * Implementations can perform a variety of tasks such as transforming, filtering, enriching, etc.
 */
public interface TextSegmentTransformer {

    /**
     * Transforms a provided segment.
     *
     * @param segment The segment to be transformed.
     * @return The transformed segment, or null if the segment should be filtered out.
     */
    TextSegment transform(TextSegment segment);

    /**
     * Transforms all the provided segments.
     *
     * @param segments A list of segments to be transformed.
     * @return A list of transformed segments. The length of this list may be shorter or longer than the original list. Returns an empty list if all segments were filtered out.
     */
    default List<TextSegment> transformAll(List<TextSegment> segments) {
        return segments.stream().map(this::transform).filter(Objects::nonNull).collect(toList());
    }

    /**
     * Transforms all the provided {@link TextSegment}s using varargs input.
     * <p>
     * This is a convenience method that allows calling code to pass an arbitrary number of {@link TextSegment}
     * instances without needing to explicitly construct a {@link List}. Internally, this method delegates to
     * the {@link #transformAll(List)} method to perform the actual transformation logic, ensuring consistent behavior.
     * </p>
     *
     * @param textSegments Varargs array of {@link TextSegment}s to be transformed. May be {@code null} or empty.
     * @return A list of transformed segments. Returns an empty list if all segments were filtered out,
     *         or if the input is {@code null} or empty.
     */
    default List<TextSegment> transformAll(TextSegment... textSegments) {
        if (Utils.isNullOrEmpty(textSegments)) {
            return List.of();
        }
        return transformAll(Arrays.asList(textSegments));
    }
}
