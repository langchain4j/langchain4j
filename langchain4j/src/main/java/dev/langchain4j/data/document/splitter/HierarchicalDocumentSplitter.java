package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.Tokenizer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.langchain4j.internal.Utils.first;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.lang.String.format;

public abstract class HierarchicalDocumentSplitter implements DocumentSplitter {

    private static final String INDEX = "index";

    protected final int maxSegmentSize;
    protected final Tokenizer tokenizer;

    protected final DocumentSplitter subSplitter;

    protected HierarchicalDocumentSplitter(int maxSegmentSizeInChars) {
        this(maxSegmentSizeInChars, null, null);
    }

    protected HierarchicalDocumentSplitter(int maxSegmentSizeInChars,
                                           DocumentSplitter subSplitter) {
        this(maxSegmentSizeInChars, null, subSplitter);
    }

    protected HierarchicalDocumentSplitter(int maxSegmentSizeInTokens,
                                           Tokenizer tokenizer) {
        this(maxSegmentSizeInTokens, tokenizer, null);
    }

    protected HierarchicalDocumentSplitter(int maxSegmentSizeInTokens,
                                           Tokenizer tokenizer,
                                           DocumentSplitter subSplitter) {
        this.maxSegmentSize = ensureGreaterThanZero(maxSegmentSizeInTokens, "maxSegmentSize");
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
        for (String part : parts) {
            if (segmentBuilder.hasSpaceFor(part)) {
                segmentBuilder.append(part);
            } else {
                if (segmentBuilder.isNotEmpty()) {
                    segments.add(createSegment(segmentBuilder.build(), document, index.getAndIncrement()));
                    segmentBuilder.reset();
                }
                if (segmentBuilder.hasSpaceFor(part)) {
                    segmentBuilder.append(part);
                } else {
                    if (subSplitter == null) {
                        throw new RuntimeException(format(
                                "The text \"%s...\" (%s %s long) doesn't fit into the maximum segment size (%s %s), " +
                                        "and there is no subSplitter defined to split it further.",
                                first(part, 30),
                                sizeOf(part), tokenizer == null ? "characters" : "tokens",
                                maxSegmentSize, tokenizer == null ? "characters" : "tokens"

                        ));
                    }
                    for (TextSegment segment : subSplitter.split(Document.from(part))) {
                        segments.add(createSegment(segment.text(), document, index.getAndIncrement()));
                        segmentBuilder.reset();
                    }
                }
            }
        }

        if (segmentBuilder.isNotEmpty()) {
            segments.add(createSegment(segmentBuilder.build(), document, index.getAndIncrement()));
            segmentBuilder.reset();
        }

        return segments;
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
