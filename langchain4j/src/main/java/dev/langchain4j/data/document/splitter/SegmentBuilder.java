package dev.langchain4j.data.document.splitter;

import java.util.function.Function;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

class SegmentBuilder {

    private StringBuilder segmentBuilder;

    private final int maxSegmentSize;
    private final Function<String, Integer> sizeFunction;

    private final String joinSeparator;

    SegmentBuilder(int maxSegmentSize, Function<String, Integer> sizeFunction, String joinSeparator) {
        this.segmentBuilder = new StringBuilder();
        this.maxSegmentSize = ensureGreaterThanZero(maxSegmentSize, "maxSegmentSize");
        this.sizeFunction = ensureNotNull(sizeFunction, "sizeFunction");
        this.joinSeparator = ensureNotNull(joinSeparator, "joinSeparator");
    }

    boolean hasSpaceFor(String text) {
        return hasSpaceFor(text, joinSeparator);
    }

    boolean hasSpaceFor(String text, String separator) {
        if (isNotEmpty()) {
            return sizeOf(segmentBuilder.toString()) + sizeOf(separator) + sizeOf(text) <= maxSegmentSize;
        } else {
            return sizeOf(text) <= maxSegmentSize;
        }
    }

    private int sizeOf(String text) {
        return sizeFunction.apply(text);
    }

    void append(String text) {
        append(text, joinSeparator);
    }

    void append(String text, String separator) {
        if (segmentBuilder.length() > 0) {
            segmentBuilder.append(separator);
        }
        segmentBuilder.append(text);
    }

    boolean isNotEmpty() {
        return segmentBuilder.length() > 0;
    }

    String build() {
        return segmentBuilder.toString().trim();
    }

    void reset() {
        segmentBuilder = new StringBuilder();
    }
}