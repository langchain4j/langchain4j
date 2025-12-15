package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;

import java.io.InputStream;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Splits the provided {@link Document} into sentences and attempts to fit as many sentences as possible
 * into a single {@link TextSegment}, adhering to the limit set by {@code maxSegmentSize}.
 * <p>
 * The {@code maxSegmentSize} can be defined in terms of characters (default) or tokens.
 * For token-based limit, a {@link TokenCountEstimator} must be provided.
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
                                      TokenCountEstimator tokenCountEstimator) {
        super(maxSegmentSizeInTokens, maxOverlapSizeInTokens, tokenCountEstimator, null);
        this.sentenceModel = createSentenceModel();
    }

    public DocumentBySentenceSplitter(int maxSegmentSizeInTokens,
                                      int maxOverlapSizeInTokens,
                                      TokenCountEstimator tokenCountEstimator,
                                      DocumentSplitter subSplitter) {
        super(maxSegmentSizeInTokens, maxOverlapSizeInTokens, tokenCountEstimator, subSplitter);
        this.sentenceModel = createSentenceModel();
    }

    /**
     * @param sentenceModel The {@link SentenceModel} to be used for splitting text into sentences.
     *                      Pretrained models for various languages can be found
     *                      <a href="https://opennlp.apache.org/models.html#sentence_detection">here</a>.
     */
    public DocumentBySentenceSplitter(int maxSegmentSizeInTokens,
                                      int maxOverlapSizeInTokens,
                                      TokenCountEstimator tokenCountEstimator,
                                      DocumentSplitter subSplitter,
                                      SentenceModel sentenceModel) {
        super(maxSegmentSizeInTokens, maxOverlapSizeInTokens, tokenCountEstimator, subSplitter);
        this.sentenceModel = ensureNotNull(sentenceModel, "sentenceModel");
    }

    private SentenceModel createSentenceModel() {
        String sentenceModelFilePath = "/opennlp/opennlp-en-ud-ewt-sentence-1.2-2.5.0.bin";
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
        return new DocumentByWordSplitter(maxSegmentSize, maxOverlapSize, tokenCountEstimator);
    }
}
