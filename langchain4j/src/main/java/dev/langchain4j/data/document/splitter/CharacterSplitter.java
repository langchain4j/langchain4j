package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSegment;
import dev.langchain4j.data.document.DocumentSplitter;

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
    public List<DocumentSegment> split(Document document) {
        if (document.text() == null || document.text().isEmpty()) {
            throw new IllegalArgumentException("Document text should not be null or empty");
        }

        String text = document.text();
        int textLength = text.length();

        if (segmentLength <= 0 || segmentOverlap < 0 || segmentLength <= segmentOverlap) {
            throw new IllegalArgumentException(String.format("Invalid segmentLength (%s) or segmentOverlap (%s)", segmentLength, segmentOverlap));
        }

        List<DocumentSegment> segments = new ArrayList<>();
        if (textLength <= segmentLength) {
            segments.add(document.toDocumentSegment());
        } else {
            for (int i = 0; i < textLength - segmentOverlap; i += segmentLength - segmentOverlap) {
                int endIndex = Math.min(i + segmentLength, textLength);
                String segment = text.substring(i, endIndex);
                segments.add(DocumentSegment.from(segment, document.metadata()));
                if (endIndex == textLength) {
                    break;
                }
            }
        }

        return segments;
    }
}
