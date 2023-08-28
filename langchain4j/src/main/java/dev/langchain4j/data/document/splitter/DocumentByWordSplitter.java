package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.Tokenizer;

/**
 * Splits the provided {@link Document} into words and attempts to fit as many words as possible
 * into a single {@link TextSegment}, adhering to the limit set by {@code maxSegmentSize}.
 * <p>
 * The {@code maxSegmentSize} can be defined in terms of characters (default) or tokens.
 * For token-based limit, a {@link Tokenizer} must be provided.
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

    public DocumentByWordSplitter(int maxSegmentSizeInChars) {
        super(maxSegmentSizeInChars, null, null);
    }

    public DocumentByWordSplitter(int maxSegmentSizeInChars, DocumentSplitter subSplitter) {
        super(maxSegmentSizeInChars, null, subSplitter);
    }

    public DocumentByWordSplitter(int maxSegmentSizeInTokens, Tokenizer tokenizer) {
        super(maxSegmentSizeInTokens, tokenizer, null);
    }

    public DocumentByWordSplitter(int maxSegmentSizeInTokens, Tokenizer tokenizer, DocumentSplitter subSplitter) {
        super(maxSegmentSizeInTokens, tokenizer, subSplitter);
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
        return new DocumentByCharacterSplitter(maxSegmentSize, tokenizer);
    }
}
