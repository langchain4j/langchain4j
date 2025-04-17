package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;

/**
 * Splits the provided {@link Document} into lines and attempts to fit as many lines as possible
 * into a single {@link TextSegment}, adhering to the limit set by {@code maxSegmentSize}.
 * <p>
 * The {@code maxSegmentSize} can be defined in terms of characters (default) or tokens.
 * For token-based limit, a {@link TokenCountEstimator} must be provided.
 * <p>
 * Line boundaries are detected by a minimum of one newline character ("\n").
 * Any additional whitespaces before or after are ignored.
 * So, the following examples are all valid line separators: "\n", "\n\n", " \n", "\n " and so on.
 * <p>
 * If multiple lines fit within {@code maxSegmentSize}, they are joined together using a newline ("\n").
 * <p>
 * If a single line is too long and exceeds {@code maxSegmentSize},
 * the {@code subSplitter} ({@link DocumentBySentenceSplitter} by default) is used to split it into smaller parts and
 * place them into multiple segments.
 * Such segments contain only the parts of the split long line.
 * <p>
 * Each {@link TextSegment} inherits all metadata from the {@link Document} and includes an "index" metadata key
 * representing its position within the document (starting from 0).
 */
public class DocumentByLineSplitter extends HierarchicalDocumentSplitter {

    public DocumentByLineSplitter(int maxSegmentSizeInChars,
                                  int maxOverlapSizeInChars) {
        super(maxSegmentSizeInChars, maxOverlapSizeInChars, null, null);
    }

    public DocumentByLineSplitter(int maxSegmentSizeInChars,
                                  int maxOverlapSizeInChars,
                                  DocumentSplitter subSplitter) {
        super(maxSegmentSizeInChars, maxOverlapSizeInChars, null, subSplitter);
    }

    public DocumentByLineSplitter(int maxSegmentSizeInTokens,
                                  int maxOverlapSizeInTokens,
                                  TokenCountEstimator tokenCountEstimator) {
        super(maxSegmentSizeInTokens, maxOverlapSizeInTokens, tokenCountEstimator, null);
    }

    public DocumentByLineSplitter(int maxSegmentSizeInTokens,
                                  int maxOverlapSizeInTokens,
                                  TokenCountEstimator tokenCountEstimator,
                                  DocumentSplitter subSplitter) {
        super(maxSegmentSizeInTokens, maxOverlapSizeInTokens, tokenCountEstimator, subSplitter);
    }

    @Override
    public String[] split(String text) {
        return text.split("\\s*\\R\\s*"); // additional whitespaces are ignored
    }

    @Override
    public String joinDelimiter() {
        return "\n";
    }

    @Override
    protected DocumentSplitter defaultSubSplitter() {
        return new DocumentBySentenceSplitter(maxSegmentSize, maxOverlapSize, tokenCountEstimator);
    }
}
