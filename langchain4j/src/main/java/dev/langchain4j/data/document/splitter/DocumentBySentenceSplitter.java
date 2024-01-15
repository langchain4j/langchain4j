package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.Tokenizer;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;

import java.io.InputStream;

/**
 * Splits the provided {@link Document} into sentences and attempts to fit as many sentences as possible
 * into a single {@link TextSegment}, adhering to the limit set by {@code maxSegmentSize}.
 * <p>
 * The {@code maxSegmentSize} can be defined in terms of characters (default) or tokens.
 * For token-based limit, a {@link Tokenizer} must be provided.
 * <p>
 * Sentence boundaries are detected using the Apache OpenNLP library with the English sentence model.
 * <p>
 * If multiple sentences fit within {@code maxSegmentSize}, they are joined together using a space (" ").
 * <p>
 * If a single sentence is too long and exceeds {@code maxSegmentSize},
 * the {@code subSplitter} ({@link DocumentByWordSplitter} by default) is used to split it into smaller parts and
 * place them into multiple segments.
 * Such segments contain only the parts of the split long sentence.
 * <p>
 * Each {@link TextSegment} inherits all metadata from the {@link Document} and includes an "index" metadata key
 * representing its position within the document (starting from 0).
 */
public class DocumentBySentenceSplitter extends HierarchicalDocumentSplitter {

    private final SentenceModel sentenceModel;

    public DocumentBySentenceSplitter(int maxSegmentSizeInChars,
                                      int maxOverlapSizeInChars) {
        super(maxSegmentSizeInChars, maxOverlapSizeInChars, null, null);
        this.sentenceModel = createSentenceModel();
    }

    public DocumentBySentenceSplitter(int maxSegmentSizeInChars,
                                      int maxOverlapSizeInChars,
                                      DocumentSplitter subSplitter) {
        super(maxSegmentSizeInChars, maxOverlapSizeInChars, null, subSplitter);
        this.sentenceModel = createSentenceModel();
    }

    public DocumentBySentenceSplitter(int maxSegmentSizeInTokens,
                                      int maxOverlapSizeInTokens,
                                      Tokenizer tokenizer) {
        super(maxSegmentSizeInTokens, maxOverlapSizeInTokens, tokenizer, null);
        this.sentenceModel = createSentenceModel();
    }

    public DocumentBySentenceSplitter(int maxSegmentSizeInTokens,
                                      int maxOverlapSizeInTokens,
                                      Tokenizer tokenizer,
                                      DocumentSplitter subSplitter) {
        super(maxSegmentSizeInTokens, maxOverlapSizeInTokens, tokenizer, subSplitter);
        this.sentenceModel = createSentenceModel();
    }

    private SentenceModel createSentenceModel() {
        String sentenceModelFilePath = "/opennlp/opennlp-en-ud-ewt-sentence-1.0-1.9.3.bin";
        try (InputStream is = getClass().getResourceAsStream(sentenceModelFilePath)) {
            return new SentenceModel(is);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String[] split(String text) {
        SentenceDetectorME sentenceDetector = new SentenceDetectorME(sentenceModel);
        return sentenceDetector.sentDetect(text);
    }

    @Override
    public String joinDelimiter() {
        return " ";
    }

    @Override
    protected DocumentSplitter defaultSubSplitter() {
        return new DocumentByWordSplitter(maxSegmentSize, maxOverlapSize, tokenizer);
    }
}
