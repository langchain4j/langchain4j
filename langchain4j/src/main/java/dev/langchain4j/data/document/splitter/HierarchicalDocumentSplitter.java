package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.langchain4j.internal.Utils.firstChars;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Base class for hierarchical document splitters.
 *
 * <p>Extends {@link DocumentSplitter} and provides machinery for sub-splitting documents
 * when a single segment is too long.
 */
public abstract class HierarchicalDocumentSplitter implements DocumentSplitter {
    private HierarchicalDocumentSplitter overlapSentenceSplitter;

    private HierarchicalDocumentSplitter getOverlapSentenceSplitter() {
        if (overlapSentenceSplitter == null) {
            overlapSentenceSplitter = new DocumentBySentenceSplitter(1, 0, null, null);
        }
        return overlapSentenceSplitter;
    }

    private static final String INDEX = "index";

    protected final int maxSegmentSize;
    protected final int maxOverlapSize;
    protected final TokenCountEstimator tokenCountEstimator;
    protected final DocumentSplitter subSplitter;

    /**
     * Creates a new instance of {@link HierarchicalDocumentSplitter}.
     *
     * @param maxSegmentSizeInChars The maximum size of a segment in characters.
     * @param maxOverlapSizeInChars The maximum size of the overlap between segments in characters.
     */
    protected HierarchicalDocumentSplitter(int maxSegmentSizeInChars, int maxOverlapSizeInChars) {
        this(maxSegmentSizeInChars, maxOverlapSizeInChars, null, null);
    }

    /**
     * Creates a new instance of {@link HierarchicalDocumentSplitter}.
     *
     * @param maxSegmentSizeInChars The maximum size of a segment in characters.
     * @param maxOverlapSizeInChars The maximum size of the overlap between segments in characters.
     * @param subSplitter           The sub-splitter to use when a single segment is too long.
     */
    protected HierarchicalDocumentSplitter(int maxSegmentSizeInChars,
                                           int maxOverlapSizeInChars,
                                           HierarchicalDocumentSplitter subSplitter) {
        this(maxSegmentSizeInChars, maxOverlapSizeInChars, null, subSplitter);
    }

    /**
     * Creates a new instance of {@link HierarchicalDocumentSplitter}.
     *
     * @param maxSegmentSizeInTokens The maximum size of a segment in tokens.
     * @param maxOverlapSizeInTokens The maximum size of the overlap between segments in tokens.
     * @param tokenCountEstimator    The {@code TokenCountEstimator} to use to estimate the number of tokens in a text.
     */
    protected HierarchicalDocumentSplitter(int maxSegmentSizeInTokens,
                                           int maxOverlapSizeInTokens,
                                           TokenCountEstimator tokenCountEstimator) {
        this(maxSegmentSizeInTokens, maxOverlapSizeInTokens, tokenCountEstimator, null);
    }

    /**
     * Creates a new instance of {@link HierarchicalDocumentSplitter}.
     *
     * @param maxSegmentSizeInTokens The maximum size of a segment in tokens.
     * @param maxOverlapSizeInTokens The maximum size of the overlap between segments in tokens.
     * @param tokenCountEstimator    The {@code TokenCountEstimator} to use to estimate the number of tokens in a text.
     * @param subSplitter            The sub-splitter to use when a single segment is too long.
     */
    protected HierarchicalDocumentSplitter(int maxSegmentSizeInTokens,
                                           int maxOverlapSizeInTokens,
                                           TokenCountEstimator tokenCountEstimator,
                                           DocumentSplitter subSplitter) {
        this.maxSegmentSize = ensureGreaterThanZero(maxSegmentSizeInTokens, "maxSegmentSize");
        this.maxOverlapSize = ensureBetween(maxOverlapSizeInTokens, 0, maxSegmentSize, "maxOverlapSize");
        this.tokenCountEstimator = tokenCountEstimator;
        this.subSplitter = subSplitter == null ? defaultSubSplitter() : subSplitter;
    }

    /**
     * Splits the provided text into parts.
     * Implementation API.
     *
     * @param text The text to be split.
     * @return An array of parts.
     */
    protected abstract String[] split(String text);

    /**
     * Delimiter string to use to re-join the parts.
     *
     * @return The delimiter.
     */
    protected abstract String joinDelimiter();

    /**
     * The default sub-splitter to use when a single segment is too long.
     *
     * @return The default sub-splitter.
     */
    protected abstract DocumentSplitter defaultSubSplitter();

    @Override
    public List<TextSegment> split(Document document) {
        ensureNotNull(document, "document");

        List<TextSegment> segments = new ArrayList<>();
        SegmentBuilder segmentBuilder = new SegmentBuilder(maxSegmentSize, this::estimateSize, joinDelimiter());
        AtomicInteger index = new AtomicInteger(0);

        String[] parts = split(document.text());
        String overlap = null;
        for (String part : parts) {
            int partSize = segmentBuilder.sizeOf(part);

            if (segmentBuilder.hasSpaceFor(partSize)) {
                // The part fits in the current segment, so we append it.
                segmentBuilder.append(part);
                continue;
            }

            if (segmentBuilder.isNotEmpty()) {
                // The part won't fit in the current segment, so we flush the current segment.
                String segmentText = segmentBuilder.toString();
                if (!segmentText.equals(overlap)) {
                    segments.add(createSegment(segmentText, document, index.getAndIncrement()));

                    overlap = overlapFrom(segmentText);

                    segmentBuilder.reset();
                    segmentBuilder.append(overlap);

                    if (segmentBuilder.hasSpaceFor(partSize)) {
                        // The part fits in the current segment, so we append it.
                        segmentBuilder.append(part);
                        continue;
                    }
                }
            }

            // Enforce that we have a sub-splitter defined.
            if (subSplitter == null) {
                throw new RuntimeException(String.format(
                        "The text \"%s...\" (%s %s long) doesn't fit into the maximum segment size (%s %s), " +
                                "and there is no subSplitter defined to split it further.",
                        firstChars(part, 30),
                        estimateSize(part), tokenCountEstimator == null ? "characters" : "tokens",
                        maxSegmentSize, tokenCountEstimator == null ? "characters" : "tokens"

                ));
            }

            // Delegate the splitting of the part to the sub-splitter.
            segmentBuilder.append(part);
            for (TextSegment segment : subSplitter.split(Document.from(segmentBuilder.toString()))) {
                segments.add(createSegment(segment.text(), document, index.getAndIncrement()));
            }

            TextSegment lastSegment = segments.get(segments.size() - 1);
            overlap = overlapFrom(lastSegment.text());

            segmentBuilder.reset();
            segmentBuilder.append(overlap);
        }

        if (segmentBuilder.isNotEmpty() && !segmentBuilder.toString().equals(overlap)) {
            segments.add(createSegment(segmentBuilder.toString(), document, index.getAndIncrement()));
        }

        return segments;
    }

    /**
     * Returns the overlap region at the end of the provided segment text.
     *
     * @param segmentText The segment text.
     * @return The overlap region, or an empty string if there is no overlap.
     */
    String overlapFrom(String segmentText) {
        if (maxOverlapSize == 0) {
            return "";
        }

        // always split by sentence, as it is the smallest meaningful unit of text
        List<String> sentences = Arrays.asList(getOverlapSentenceSplitter().split(segmentText));
        Collections.reverse(sentences);

        SegmentBuilder overlapBuilder = new SegmentBuilder(maxOverlapSize, this::estimateSize, joinDelimiter());
        for (String sentence : sentences) {
            if (overlapBuilder.hasSpaceFor(sentence)) {
                overlapBuilder.prepend(sentence);
            } else {
                break;
            }
        }
        return overlapBuilder.toString();
    }

    /**
     * Estimates the size in the provided text.
     *
     * <p>If a {@link TokenCountEstimator} is provided, the number of tokens is estimated.
     * Otherwise, the number of characters is estimated.
     *
     * @param text The text.
     * @return The estimated number of tokens.
     */
    int estimateSize(String text) {
        if (tokenCountEstimator != null) {
            return tokenCountEstimator.estimateTokenCountInText(text);
        } else {
            return text.length();
        }
    }

    /**
     * Creates a new {@link TextSegment} from the provided text and document.
     *
     * <p>The segment inherits all metadata from the document. The segment also includes
     * an "index" metadata key representing the segment position within the document.
     *
     * @param text     The text of the segment.
     * @param document The document to which the segment belongs.
     * @param index    The index of the segment within the document.
     */
    static TextSegment createSegment(String text, Document document, int index) {
        Metadata metadata = document.metadata().copy().put(INDEX, String.valueOf(index));
        return TextSegment.from(text, metadata);
    }
}
