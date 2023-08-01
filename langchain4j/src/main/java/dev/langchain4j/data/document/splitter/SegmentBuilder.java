package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;

import java.util.function.Function;

class SegmentBuilder {

    private StringBuilder sb;

    private final int maxSegmentSize;
    private final Function<String, Integer> sizeFunction;

    private final String separator;

    SegmentBuilder(int maxSegmentSize, Function<String, Integer> sizeFunction, String separator) {
        this.sb = new StringBuilder();
        this.maxSegmentSize = maxSegmentSize;
        this.sizeFunction = sizeFunction;
        this.separator = separator;
    }

    boolean hasSpaceFor(String text) {
        return sizeOf(sb.toString()) + sizeOf(separator) + sizeOf(text) <= maxSegmentSize;
    }

    int sizeOf(String text) {
        return sizeFunction.apply(text);
    }

    void append(String text) {
        if (sb.length() > 0) {
            sb.append(separator);
        }
        sb.append(text);
    }

    boolean isNotEmpty() {
        return sb.length() > 0;
    }

    TextSegment buildWith(Metadata metadata) {
        return TextSegment.from(sb.toString().trim(), metadata);
    }

    void refresh() {
        sb = new StringBuilder();
    }
}