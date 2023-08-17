package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.Tokenizer;

import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.data.document.Document.document;

/**
 * Splits provided text into paragraphs and tries to fit as many paragraphs into one {@link TextSegment} as dictated by {@link ParagraphSplitter#maxSegmentSize}.
 * maxSegmentSize can be defined in terms of chars (default) or tokens. For tokens, you need to provide a {@link Tokenizer}.
 * Paragraphs should be separated by at least two newlines to be detected. Other whitespaces aside from two newlines are ignored.
 * If multiple paragraphs fit into maxSegmentSize, they are concatenated by two newlines.
 * If some paragraph does not fit into maxSegmentSize, it is split into multiple segments using {@link SentenceSplitter}.
 * These segments are standalone and do not contain other text other than split sentences.
 */
// TODO name
public class ParagraphSplitter implements DocumentSplitter {

    private static final String AT_LEAST_TWO_NEWLINES = "\\s*\\R\\s*\\R\\s*"; // ignoring extra whitespaces
    private static final String SEPARATOR = "\n\n";

    private final int maxSegmentSize;
    private final Tokenizer tokenizer;
    private final SentenceSplitter sentenceSplitter;

    public ParagraphSplitter(int maxSegmentSize) {
        this(maxSegmentSize, null);
    }

    public ParagraphSplitter(int maxSegmentSize, Tokenizer tokenizer) {
        this.maxSegmentSize = maxSegmentSize;
        this.tokenizer = tokenizer;
        this.sentenceSplitter = new SentenceSplitter(maxSegmentSize, tokenizer);
    }

    @Override
    public List<TextSegment> split(Document document) {
        String text = document.text();

        String[] paragraphs = text.split(AT_LEAST_TWO_NEWLINES);

        List<TextSegment> textSegments = new ArrayList<>();

        SegmentBuilder segmentBuilder = new SegmentBuilder(maxSegmentSize, this::sizeOf, SEPARATOR);

        for (String paragraph : paragraphs) {
            if (segmentBuilder.hasSpaceFor(paragraph)) {
                segmentBuilder.append(paragraph);
            } else {
                if (segmentBuilder.isNotEmpty()) {
                    TextSegment segment = segmentBuilder.buildWith(document.metadata());
                    textSegments.add(segment);
                    segmentBuilder.refresh();
                }
                if (sizeOf(paragraph) <= maxSegmentSize) {
                    segmentBuilder.append(paragraph);
                } else {
                    // Paragraph does not fit into segment.
                    // Need to split it into sentences and distribute them between multiple segments.
                    List<TextSegment> sentenceSegments = sentenceSplitter.split(document(paragraph, document.metadata()));
                    textSegments.addAll(sentenceSegments);
                }
            }
        }

        if (segmentBuilder.isNotEmpty()) {
            TextSegment segment = segmentBuilder.buildWith(document.metadata());
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
