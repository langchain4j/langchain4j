package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.Tokenizer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.langchain4j.internal.Utils.firstChars;
import static dev.langchain4j.internal.ValidationUtils.*;
import static java.lang.String.format;

public abstract class HierarchicalDocumentSplitter implements DocumentSplitter {

    private static final String INDEX = "index";

    protected final int maxSegmentSize;
    protected final int maxOverlapSize;
    protected final Tokenizer tokenizer;

    protected final DocumentSplitter subSplitter;

    protected HierarchicalDocumentSplitter(int maxSegmentSizeInChars, int maxOverlapSizeInChars) {
        this(maxSegmentSizeInChars, maxOverlapSizeInChars, null, null);
    }

    protected HierarchicalDocumentSplitter(int maxSegmentSizeInChars,
                                           int maxOverlapSizeInChars,
                                           HierarchicalDocumentSplitter subSplitter) {
        this(maxSegmentSizeInChars, maxOverlapSizeInChars, null, subSplitter);
    }

    protected HierarchicalDocumentSplitter(int maxSegmentSizeInTokens,
                                           int maxOverlapSizeInTokens,
                                           Tokenizer tokenizer) {
        this(maxSegmentSizeInTokens, maxOverlapSizeInTokens, tokenizer, null);
    }

    protected HierarchicalDocumentSplitter(int maxSegmentSizeInTokens,
                                           int maxOverlapSizeInTokens,
                                           Tokenizer tokenizer,
                                           DocumentSplitter subSplitter) {
        this.maxSegmentSize = ensureGreaterThanZero(maxSegmentSizeInTokens, "maxSegmentSize");
        this.maxOverlapSize = ensureBetween(maxOverlapSizeInTokens, 0, maxSegmentSize, "maxOverlapSize");
        this.tokenizer = tokenizer;
        this.subSplitter = subSplitter == null ? defaultSubSplitter() : subSplitter;
    }

    protected abstract String[] split(String text);

    protected abstract String joinDelimiter();

    protected abstract DocumentSplitter defaultSubSplitter();

    @Override
    public List<TextSegment> split(Document document) {
        ensureNotNull(document, "document");

        List<TextSegment> segments = new ArrayList<>();
        SegmentBuilder segmentBuilder = new SegmentBuilder(maxSegmentSize, this::sizeOf, joinDelimiter());
        AtomicInteger index = new AtomicInteger(0);

        String[] parts = split(document.text());
        String overlap = null;
        for (String part : parts) {
            if (segmentBuilder.hasSpaceFor(part)) {
                segmentBuilder.append(part);
            } else {
                if (segmentBuilder.isNotEmpty() && !segmentBuilder.build().equals(overlap)) {
                    String segmentText = segmentBuilder.build();
                    segments.add(createSegment(segmentText, document, index.getAndIncrement()));
                    segmentBuilder.reset();
                    overlap = overlapFrom(segmentText);
                    segmentBuilder.append(overlap);
                }
                if (segmentBuilder.hasSpaceFor(part)) {
                    segmentBuilder.append(part);
                } else {
                    if (subSplitter == null) {
                        throw new RuntimeException(format(
                                "The text \"%s...\" (%s %s long) doesn't fit into the maximum segment size (%s %s), " +
                                        "and there is no subSplitter defined to split it further.",
                                firstChars(part, 30),
                                sizeOf(part), tokenizer == null ? "characters" : "tokens",
                                maxSegmentSize, tokenizer == null ? "characters" : "tokens"

                        ));
                    }
                    segmentBuilder.append(part);
                    for (TextSegment segment : subSplitter.split(Document.from(segmentBuilder.build()))) {
                        segments.add(createSegment(segment.text(), document, index.getAndIncrement()));
                    }
                    segmentBuilder.reset();
                    TextSegment lastSegment = segments.get(segments.size() - 1);
                    overlap = overlapFrom(lastSegment.text());
                    segmentBuilder.append(overlap);
                }
            }
        }

        if (segmentBuilder.isNotEmpty() && !segmentBuilder.build().equals(overlap)) {
            segments.add(createSegment(segmentBuilder.build(), document, index.getAndIncrement()));
        }

        return segments;
    }

    private String overlapFrom(String segmentText) {
        if (maxOverlapSize == 0) {
            return "";
        }

        SegmentBuilder overlapBuilder = new SegmentBuilder(maxOverlapSize, this::sizeOf, joinDelimiter());

        String[] sentences = new DocumentBySentenceSplitter(1, 0, null, null).split(segmentText);
        for (int i = sentences.length - 1; i >= 0; i--) {
            String part = sentences[i];
            if (overlapBuilder.hasSpaceFor(part)) {
                overlapBuilder.prepend(part);
            } else {
                return overlapBuilder.build();
            }
        }

        return "";
    }

    private int sizeOf(String text) {
        if (tokenizer != null) {
            return tokenizer.estimateTokenCountInText(text);
        } else {
            return text.length();
        }
    }

    private static TextSegment createSegment(String text, Document document, int index) {
        Metadata metadata = document.metadata().copy().add(INDEX, index);
        return TextSegment.from(text, metadata);
    }
}
