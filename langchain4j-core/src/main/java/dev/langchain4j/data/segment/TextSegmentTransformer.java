package dev.langchain4j.data.segment;

import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toList;

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
        return segments.stream()
                .map(this::transform)
                .filter(Objects::nonNull)
                .collect(toList());
    }
}