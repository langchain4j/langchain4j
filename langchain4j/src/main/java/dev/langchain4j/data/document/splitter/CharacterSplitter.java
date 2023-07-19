package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;

import java.util.ArrayList;
import java.util.List;

public class CharacterSplitter implements DocumentSplitter {

    private final int segmentLength;
    private final int segmentOverlap;

    public CharacterSplitter(int segmentLength, int segmentOverlap) {
        this.segmentLength = segmentLength;
        this.segmentOverlap = segmentOverlap;
    }

    @Override
    public List<TextSegment> split(Document document) {
        if (document.text() == null || document.text().isEmpty()) {
            throw new IllegalArgumentException("Document text should not be null or empty");
        }

        String text = document.text();
        int textLength = text.length();

        if (segmentLength <= 0 || segmentOverlap < 0 || segmentLength <= segmentOverlap) {
            throw new IllegalArgumentException(String.format("Invalid segmentLength (%s) or segmentOverlap (%s)", segmentLength, segmentOverlap));
        }

        int segmentIndex = 0;
        List<TextSegment> segments = new ArrayList<>();
        if (textLength <= segmentLength) {
            segments.add(document.toTextSegment());
        } else {
            for (int startIndex = 0;
                 startIndex < textLength - segmentOverlap;
                 startIndex += segmentLength - segmentOverlap
            ) {
                int stopIndex = Math.min(startIndex + segmentLength, textLength);
                String segment = text.substring(startIndex, stopIndex);
                Metadata metadata = document.metadata().copy();
                metadata.add("index", String.valueOf(segmentIndex++)); // TODO names
                metadata.add("start_offset", String.valueOf(startIndex)); // TODO names
                metadata.add("end_offset", String.valueOf(stopIndex)); // TODO names
                segments.add(TextSegment.from(segment, metadata));
                if (stopIndex == textLength) {
                    break;
                }
            }
        }

        return segments;
    }
}
