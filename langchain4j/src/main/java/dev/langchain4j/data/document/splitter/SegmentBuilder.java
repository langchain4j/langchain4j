package dev.langchain4j.data.document.splitter;

import dev.langchain4j.Internal;

import java.util.function.Function;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Segment builder utility class for HierarchicalDocumentSplitter.
 */
@Internal
class SegmentBuilder {

    private final int maxSegmentSize;
    private final Function<String, Integer> sizeFunction;
    private final String joinSeparator;
    private final int joinSeparatorSize;
    private String segment = "";
    private int segmentSize = 0;

    /**
     * Creates a new instance of {@link SegmentBuilder}.
     *
     * @param maxSegmentSize The maximum size of a segment.
     * @param sizeFunction   The function to use to estimate the size of a text.
     * @param joinSeparator  The separator to use when joining multiple texts into a single segment.
     */
    public SegmentBuilder(int maxSegmentSize, Function<String, Integer> sizeFunction, String joinSeparator) {
        this.maxSegmentSize = ensureGreaterThanZero(maxSegmentSize, "maxSegmentSize");
        this.sizeFunction = ensureNotNull(sizeFunction, "sizeFunction");
        this.joinSeparator = ensureNotNull(joinSeparator, "joinSeparator");
        this.joinSeparatorSize = sizeOf(joinSeparator);
    }

    /**
     * Returns the current size of the segment (as returned by the {@code sizeFunction}).
     *
     * @return The current size of the segment.
     */
    public int getSize() {
        return segmentSize;
    }

    /**
     * Returns {@code true} if the provided text can be added to the current segment.
     *
     * @param text The text to check.
     * @return {@code true} if the provided text can be added to the current segment.
     */
    public boolean hasSpaceFor(String text) {
        int totalSize = sizeOf(text);
        if (isNotEmpty()) {
            totalSize += segmentSize + joinSeparatorSize;
        }
        return totalSize <= maxSegmentSize;
    }

    /**
     * Returns {@code true} if the provided size can be added to the current segment.
     *
     * @param size The size to check.
     * @return {@code true} if the provided size can be added to the current segment.
     */
    public boolean hasSpaceFor(int size) {
        int totalSize = size;
        if (isNotEmpty()) {
            totalSize += segmentSize + joinSeparatorSize;
        }
        return totalSize <= maxSegmentSize;
    }

    /**
     * Returns the size of the provided text (as returned by the {@code sizeFunction}).
     *
     * @param text The text to check.
     * @return The size of the provided text.
     */
    public int sizeOf(String text) {
        return sizeFunction.apply(text);
    }

    /**
     * Appends the provided text to the current segment.
     *
     * @param text The text to append.
     */
    public void append(String text) {
        if (isNotEmpty()) {
            segment += joinSeparator;
        }
        segment += text;
        segmentSize = sizeOf(segment);
    }

    /**
     * Prepends the provided text to the current segment.
     *
     * @param text The text to prepend.
     */
    public void prepend(String text) {
        if (isNotEmpty()) {
            segment = text + joinSeparator + segment;
        } else {
            segment = text;
        }
        segmentSize = sizeOf(segment);
    }

    /**
     * Returns {@code true} if the current segment is not empty.
     *
     * @return {@code true} if the current segment is not empty.
     */
    public boolean isNotEmpty() {
        return !segment.isEmpty();
    }

    @Override
    public String toString() {
        return segment.trim();
    }

    /**
     * Resets the current segment.
     */
    public void reset() {
        segment = "";
        segmentSize = 0;
    }
}
