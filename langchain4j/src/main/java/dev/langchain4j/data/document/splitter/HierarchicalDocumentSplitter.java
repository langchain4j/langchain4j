package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.Tokenizer;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Utils.firstChars;
import static dev.langchain4j.internal.ValidationUtils.*;

/**
 * Base class for hierarchical document splitters.
 *
 * <p>Extends {@link DocumentSplitter} and provides machinery for sub-splitting documents
 * when a single segment is too long.
 */
public abstract class HierarchicalDocumentSplitter implements DocumentSplitter {
    @Getter(lazy = true)
    private final HierarchicalDocumentSplitter overlapSentenceSplitter =
            new DocumentBySentenceSplitter(1, 0, null, null, false);

    private static final String INDEX = "index";
    private static final String CHARACTER_START_INDEX = "character_start_index";

    protected final int maxSegmentSize;
    protected final int maxOverlapSize;
    protected final Tokenizer tokenizer;
    protected final DocumentSplitter subSplitter;
    protected final Boolean addCharacterStartIndex;


    /**
     * Creates a new instance of {@link HierarchicalDocumentSplitter}.
     * @param maxSegmentSizeInChars The maximum size of a segment in characters.
     * @param maxOverlapSizeInChars The maximum size of the overlap between segments in characters.
     */
    protected HierarchicalDocumentSplitter(int maxSegmentSizeInChars, int maxOverlapSizeInChars) {
        this(maxSegmentSizeInChars, maxOverlapSizeInChars, null, null, false);
    }

    /**
     * Creates a new instance of {@link HierarchicalDocumentSplitter}.
     * @param maxSegmentSizeInChars The maximum size of a segment in characters.
     * @param maxOverlapSizeInChars The maximum size of the overlap between segments in characters.
     * @param subSplitter           The sub-splitter to use when a single segment is too long.
     */
    protected HierarchicalDocumentSplitter(int maxSegmentSizeInChars,
                                           int maxOverlapSizeInChars,
                                           HierarchicalDocumentSplitter subSplitter) {
        this(maxSegmentSizeInChars, maxOverlapSizeInChars, null, subSplitter, false);
    }

    /**
     * Creates a new instance of {@link HierarchicalDocumentSplitter}.
     * @param maxSegmentSizeInTokens The maximum size of a segment in tokens.
     * @param maxOverlapSizeInTokens The maximum size of the overlap between segments in tokens.
     * @param tokenizer              The tokenizer to use to estimate the number of tokens in a text.
     */
    protected HierarchicalDocumentSplitter(int maxSegmentSizeInTokens,
                                           int maxOverlapSizeInTokens,
                                           Tokenizer tokenizer) {
        this(maxSegmentSizeInTokens, maxOverlapSizeInTokens, tokenizer, null, false);
    }

    /**
     * Creates a new instance of {@link HierarchicalDocumentSplitter}.
     *
     * @param maxSegmentSizeInTokens The maximum size of a segment in tokens.
     * @param maxOverlapSizeInTokens The maximum size of the overlap between segments in tokens.
     * @param tokenizer              The tokenizer to use to estimate the number of tokens in a text.
     * @param subSplitter            The sub-splitter to use when a single segment is too long.
     * @param addCharacterStartIndex Whether to add the character start index to the segment.
     */
    protected HierarchicalDocumentSplitter(int maxSegmentSizeInTokens,
                                           int maxOverlapSizeInTokens,
                                           Tokenizer tokenizer,
                                           DocumentSplitter subSplitter,
                                           Boolean addCharacterStartIndex) {
        this.maxSegmentSize = ensureGreaterThanZero(maxSegmentSizeInTokens, "maxSegmentSize");
        this.maxOverlapSize = ensureBetween(maxOverlapSizeInTokens, 0, maxSegmentSize, "maxOverlapSize");
        this.tokenizer = tokenizer;
        this.subSplitter = subSplitter == null ? defaultSubSplitter() : subSplitter;
        this.addCharacterStartIndex = addCharacterStartIndex != null && addCharacterStartIndex;
    }

    /**
     * Splits the provided text into parts.
     * Implementation API.
     * @param text The text to be split.
     * @return An array of parts.
     */
    protected abstract String[] split(String text);

    /**
     * Delimiter string to use to re-join the parts.
     * @return The delimiter.
     */
    protected abstract String joinDelimiter();

    /**
     * The default sub-splitter to use when a single segment is too long.
     * @return The default sub-splitter.
     */
    protected abstract DocumentSplitter defaultSubSplitter();

    @Override
    public List<TextSegment> split(Document document) {
        ensureNotNull(document, "document");

        List<TextSegment> segments = new ArrayList<>();
        SegmentBuilder segmentBuilder = new SegmentBuilder(maxSegmentSize, this::estimateSize, joinDelimiter());
        AtomicInteger index = new AtomicInteger(0);
        AtomicInteger characterStartIndex = new AtomicInteger(0);

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
                    if (addCharacterStartIndex) {
                        segments.add(createSegment(segmentText, document, index.getAndIncrement(), characterStartIndex.getAndAdd(segmentText.length() + 1)));
                    } else {
                        segments.add(createSegment(segmentText, document, index.getAndIncrement()));
                    }

                    overlap = overlapFrom(segmentText);
                    int overlapLength = !overlap.isEmpty() ? overlap.length() + 1 : 0;
                    characterStartIndex.getAndAdd(-overlapLength);

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
                        estimateSize(part), tokenizer == null ? "characters" : "tokens",
                        maxSegmentSize, tokenizer == null ? "characters" : "tokens"

                ));
            }

            // `part` cannot fit into `segmentBuilder`, there are no parts already in `segmentBuilder` => Delegate the splitting of the part to the `subSplitter`.
            segmentBuilder.append(part);
            int characterStartIndexBeforeSubSplit = characterStartIndex.get();
            for (TextSegment segment : subSplitter.split(Document.from(segmentBuilder.toString()))) {
                TextSegment segmentToAdd;
                if (addCharacterStartIndex) {
                    segmentToAdd = createSegment(segment.text(), document, index.getAndIncrement(), characterStartIndexBeforeSubSplit + segment.metadata().getInteger(CHARACTER_START_INDEX));
                } else {
                    segmentToAdd = createSegment(segment.text(), document, index.getAndIncrement());
                }
                segments.add(segmentToAdd);
            }

            TextSegment lastSegment = segments.get(segments.size() - 1);
            overlap = overlapFrom(lastSegment.text());
            int overlapLength = !overlap.isEmpty() ? overlap.length() + 1 : 0;
            if (addCharacterStartIndex) {
                characterStartIndex.set(lastSegment.metadata().getInteger(CHARACTER_START_INDEX) + lastSegment.text().length() + 1 - overlapLength);
            }

            segmentBuilder.reset();
            segmentBuilder.append(overlap);
        }

        if (segmentBuilder.isNotEmpty() && !segmentBuilder.toString().equals(overlap)) {
            String segmentText = segmentBuilder.toString();
            TextSegment segmentToAdd;
            if (addCharacterStartIndex) {
                segmentToAdd = createSegment(segmentText, document, index.getAndIncrement(), characterStartIndex.getAndAdd(segmentText.length() + 1));
            } else {
                segmentToAdd = createSegment(segmentText, document, index.getAndIncrement());
            }
            segments.add(segmentToAdd);
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
     * <p>If a {@link Tokenizer} is provided, the number of tokens is estimated.
     * Otherwise, the number of characters is estimated.
     *
     * @param text The text.
     * @return The estimated number of tokens.
     */
    int estimateSize(String text) {
        if (tokenizer != null) {
            return tokenizer.estimateTokenCountInText(text);
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

    /**
     * Creates a new {@link TextSegment} from the provided text and document.
     *
     * <p>The segment inherits all metadata from the document. The segment also includes
     * an "index" metadata key representing the segment position within the document.
     *
     * @param text                The text of the segment.
     * @param document            The document to which the segment belongs.
     * @param index               The index of the segment within the document.
     * @param characterStartIndex The index of the start character within the document.
     */
    static TextSegment createSegment(String text, Document document, int index, int characterStartIndex) {
        Metadata metadata = document.metadata().copy().put(INDEX, String.valueOf(index)).put(CHARACTER_START_INDEX, String.valueOf(characterStartIndex));
        return TextSegment.from(text, metadata);
    }
}
