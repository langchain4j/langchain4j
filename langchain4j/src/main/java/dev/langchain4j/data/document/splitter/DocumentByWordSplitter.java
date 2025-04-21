package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;

/**
 * Splits the provided {@link Document} into words and attempts to fit as many words as possible
 * into a single {@link TextSegment}, adhering to the limit set by {@code maxSegmentSize}.
 * <p>
 * The {@code maxSegmentSize} can be defined in terms of characters (default) or tokens.
 * For token-based limit, a {@link TokenCountEstimator} must be provided.
 * <p>
 * Word boundaries are detected by a minimum of one space (" ").
 * Any additional whitespaces before or after are ignored.
 * So, the following examples are all valid word separators: " ", "  ", "\n", and so on.
 * <p>
 * If multiple words fit within {@code maxSegmentSize}, they are joined together using a space (" ").
 * <p>
 * Although this should not happen, if a single word is too long and exceeds {@code maxSegmentSize},
 * the {@code subSplitter} ({@link DocumentByCharacterSplitter} by default) is used to split it into smaller parts and
 * place them into multiple segments.
 * Such segments contain only the parts of the split long word.
 * <p>
 * Each {@link TextSegment} inherits all metadata from the {@link Document} and includes an "index" metadata key
 * representing its position within the document (starting from 0).
 */
public class DocumentByWordSplitter extends HierarchicalDocumentSplitter {

    public DocumentByWordSplitter(int maxSegmentSizeInChars,
                                  int maxOverlapSizeInChars) {
        super(maxSegmentSizeInChars, maxOverlapSizeInChars, null, null);
    }

    public DocumentByWordSplitter(int maxSegmentSizeInChars,
                                  int maxOverlapSizeInChars,
                                  DocumentSplitter subSplitter) {
        super(maxSegmentSizeInChars, maxOverlapSizeInChars, null, subSplitter);
    }

    public DocumentByWordSplitter(int maxSegmentSizeInTokens,
                                  int maxOverlapSizeInTokens,
                                  TokenCountEstimator tokenCountEstimator) {
        super(maxSegmentSizeInTokens, maxOverlapSizeInTokens, tokenCountEstimator, null);
    }

    public DocumentByWordSplitter(int maxSegmentSizeInTokens,
                                  int maxOverlapSizeInTokens,
                                  TokenCountEstimator tokenCountEstimator,
                                  DocumentSplitter subSplitter) {
        super(maxSegmentSizeInTokens, maxOverlapSizeInTokens, tokenCountEstimator, subSplitter);
    }

    @Override
    public String[] split(String text) {
        return text.split("\\s+"); // additional whitespaces are ignored
    }

    @Override
    public String joinDelimiter() {
        return " ";
    }

    @Override
    protected DocumentSplitter defaultSubSplitter() {
        return new DocumentByCharacterSplitter(maxSegmentSize, maxOverlapSize, tokenCountEstimator);
    }
}
