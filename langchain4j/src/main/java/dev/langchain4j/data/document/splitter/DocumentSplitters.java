package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.model.Tokenizer;

public class DocumentSplitters {

    /**
     * This is a recommended {@link DocumentSplitter} for generic text.
     * It tries to split the document into paragraphs first and fits
     * as many paragraphs into a single {@link dev.langchain4j.data.segment.TextSegment} as possible.
     * If some paragraphs are too long, they are recursively split into lines, then sentences,
     * then words, and then characters until they fit into a segment.
     *
     * @param maxSegmentSizeInTokens The maximum size of the segment, defined in tokens.
     * @param tokenizer              The tokenizer that is used to count tokens in the text.
     * @return recursive document splitter
     */
    public static DocumentSplitter recursive(int maxSegmentSizeInTokens, Tokenizer tokenizer) {
        return new DocumentByParagraphSplitter(maxSegmentSizeInTokens, tokenizer,
                new DocumentByLineSplitter(maxSegmentSizeInTokens, tokenizer,
                        new DocumentBySentenceSplitter(maxSegmentSizeInTokens, tokenizer,
                                new DocumentByWordSplitter(maxSegmentSizeInTokens, tokenizer))));
    }

    /**
     * This is a recommended {@link DocumentSplitter} for generic text.
     * It tries to split the document into paragraphs first and fits
     * as many paragraphs into a single {@link dev.langchain4j.data.segment.TextSegment} as possible.
     * If some paragraphs are too long, they are recursively split into lines, then sentences,
     * then words, and then characters until they fit into a segment.
     *
     * @param maxSegmentSizeInChars The maximum size of the segment, defined in characters.
     * @return recursive document splitter
     */
    public static DocumentSplitter recursive(int maxSegmentSizeInChars) {
        return recursive(maxSegmentSizeInChars, null);
    }
}
