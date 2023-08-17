package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.Tokenizer;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits provided text into sentences and tries to fit as many sentences into one {@link TextSegment} as allowed by {@link SentenceSplitter#maxSegmentSize}.
 * {@link SentenceSplitter#maxSegmentSize} can be defined in terms of chars (default) or tokens. For tokens, you need to provide {@link Tokenizer}.
 * Sentences are detected using Apache OpenNLP library.
 * If multiple sentences fit into maxSegmentSize, they are concatenated by a space.
 * If some sentence does not fit into maxSegmentSize, it is split into words and distributed into multiple segments.
 * These segments are standalone and do not contain other text other than parts of split sentence.
 */
public class SentenceSplitter implements DocumentSplitter {

    // TODO split long sentences if they dont fit into maxSegmentSize
    // TODO preserve formatting between sentences? e.g. newlines

    private static final String SEPARATOR = " ";

    private final int maxSegmentSize;
    private final Tokenizer tokenizer;
    private final SentenceDetectorME sentenceDetector;

    public SentenceSplitter(int maxSegmentSize) {
        this(maxSegmentSize, null);
    }

    public SentenceSplitter(int maxSegmentSize, Tokenizer tokenizer) {
        this.maxSegmentSize = maxSegmentSize;
        this.tokenizer = tokenizer;
        try (InputStream is = getClass().getResourceAsStream("/opennlp/en-sent.bin")) {
            this.sentenceDetector = new SentenceDetectorME(new SentenceModel(is));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<TextSegment> split(Document document) {
        String text = document.text();

        String[] sentences = sentenceDetector.sentDetect(text);

        List<TextSegment> textSegments = new ArrayList<>();

        SegmentBuilder segmentBuilder = new SegmentBuilder(maxSegmentSize, this::sizeOf, SEPARATOR);

        int offset = 0;
        for (String sentence : sentences) {
            if (segmentBuilder.hasSpaceFor(sentence)) {
                segmentBuilder.append(sentence);
            } else {
                if (segmentBuilder.isNotEmpty()) {
                    TextSegment segment = segmentBuilder.buildWith(document.metadata().copy().add("end_offset", offset));
                    textSegments.add(segment);
                    segmentBuilder.refresh();
                }
                if (sizeOf(sentence) <= maxSegmentSize) {
                    segmentBuilder.append(sentence);
                } else {
                    // The sentence does not fit into a segment.
                    // Need to split it into words and distribute them between multiple segments.
                    String[] words = sentence.split("\\s+");
                    for (String word : words) {
                        if (segmentBuilder.hasSpaceFor(word)) {
                            segmentBuilder.append(word);
                        } else {
                            TextSegment segment = segmentBuilder.buildWith(document.metadata().copy().add("end_offset", offset));
                            textSegments.add(segment);
                            segmentBuilder.refresh();
                        }
                    }
                }
            }
            offset++;
        }

        if (segmentBuilder.isNotEmpty()) {
            TextSegment segment = segmentBuilder.buildWith(document.metadata().copy().add("end_offset", offset));
            textSegments.add(segment);
        }

        return textSegments;
    }

    private int sizeOf(String text) {
        if (tokenizer != null) {
            return tokenizer.estimateTokenCountInText(text);
        }
        return text.length();
    }
}
