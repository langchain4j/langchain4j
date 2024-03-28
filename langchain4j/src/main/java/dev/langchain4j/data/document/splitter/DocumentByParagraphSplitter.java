package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.Tokenizer;

/**
 * Splits the provided {@link Document} into paragraphs and attempts to fit as many paragraphs as possible
 * into a single {@link TextSegment}, adhering to the limit set by {@code maxSegmentSize}.
 * <p>
 * The {@code maxSegmentSize} can be defined in terms of characters (default) or tokens.
 * For token-based limit, a {@link Tokenizer} must be provided.
 * <p>
 * Paragraph boundaries are detected by a minimum of two newline characters ("\n\n").
 * Any additional whitespaces before, between, or after are ignored.
 * So, the following examples are all valid paragraph separators: "\n\n", "\n\n\n", "\n \n", " \n \n ", and so on.
 * <p>
 * If multiple paragraphs fit within {@code maxSegmentSize}, they are joined together using a double newline ("\n\n").
 * <p>
 * If a single paragraph is too long and exceeds {@code maxSegmentSize},
 * the {@code subSplitter} ({@link DocumentBySentenceSplitter} by default) is used to split it into smaller parts and
 * place them into multiple segments.
 * Such segments contain only the parts of the split long paragraph.
 * <p>
 * Each {@link TextSegment} inherits all metadata from the {@link Document} and includes an "index" metadata key
 * representing its position within the document (starting from 0).
 */
public class DocumentByParagraphSplitter extends HierarchicalDocumentSplitter {

    public DocumentByParagraphSplitter(int maxSegmentSizeInChars,
                                       int maxOverlapSizeInChars) {
        super(maxSegmentSizeInChars, maxOverlapSizeInChars, null, null);
    }

    public DocumentByParagraphSplitter(int maxSegmentSizeInChars,
                                       int maxOverlapSizeInChars,
                                       DocumentSplitter subSplitter) {
        super(maxSegmentSizeInChars, maxOverlapSizeInChars, null, subSplitter);
    }

    public DocumentByParagraphSplitter(int maxSegmentSizeInTokens,
                                       int maxOverlapSizeInTokens,
                                       Tokenizer tokenizer) {
        super(maxSegmentSizeInTokens, maxOverlapSizeInTokens, tokenizer, null);
    }

    public DocumentByParagraphSplitter(int maxSegmentSizeInTokens,
                                       int maxOverlapSizeInTokens,
                                       Tokenizer tokenizer,
                                       DocumentSplitter subSplitter) {
        super(maxSegmentSizeInTokens, maxOverlapSizeInTokens, tokenizer, subSplitter);
    }

    @Override
    public String[] split(String text) {
        return text.split("\\s*(?>\\R)\\s*(?>\\R)\\s*"); // additional whitespaces are ignored
    }

    @Override
    public String joinDelimiter() {
        return "\n\n";
    }

    @Override
    protected DocumentSplitter defaultSubSplitter() {
        return new DocumentBySentenceSplitter(maxSegmentSize, maxOverlapSize, tokenizer);
    }
}
