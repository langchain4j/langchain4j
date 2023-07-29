package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.data.document.Document.document;
import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

class SentenceSplitterTest {

    @Test
    void should_split_into_segments_with_one_sentence_per_segment() {

        int maxSegmentSize = 30;

        String firstSentence = "This is a first sentence.";
        assertThat(firstSentence).hasSizeLessThan(maxSegmentSize);

        String secondSentence = "This is a second sentence.";
        assertThat(secondSentence).hasSizeLessThan(maxSegmentSize);

        assertThat(firstSentence + " " + secondSentence).hasSizeGreaterThan(maxSegmentSize);

        Document document = document(format(" %s  %s ", firstSentence, secondSentence));

        SentenceSplitter splitter = new SentenceSplitter(maxSegmentSize);

        List<TextSegment> segments = splitter.split(document);

        assertThat(segments).containsExactly(
                textSegment(firstSentence),
                textSegment(secondSentence)
        );
    }

    // TODO with tokenizer

    @Test
    void should_split_into_segments_with_multiple_sentences_per_segment() {

        int maxSegmentSize = 60;

        String firstSentence = "This is a first sentence.";
        String secondSentence = "This is a second sentence.";
        assertThat(firstSentence + " " + secondSentence).hasSizeLessThan(maxSegmentSize);

        String thirdSentence = "This is a third sentence.";
        assertThat(firstSentence + " " + secondSentence + " " + thirdSentence)
                .hasSizeGreaterThan(maxSegmentSize);

        Document document = document(format(" %s  %s  %s ", firstSentence, secondSentence, thirdSentence));

        SentenceSplitter splitter = new SentenceSplitter(maxSegmentSize);

        List<TextSegment> segments = splitter.split(document);

        assertThat(segments).containsExactly(
                textSegment(firstSentence + " " + secondSentence),
                textSegment(thirdSentence)
        );
    }

    @Test
    void should_split_sentence_if_it_does_not_fit_into_segment() {

        int maxSegmentSize = 40;

        String firstSentence = "This is a short sentence.";
        assertThat(firstSentence).hasSizeLessThan(maxSegmentSize);

        String secondSentence = "This is a very long sentence that does   not fit into segment.";
        assertThat(secondSentence).hasSizeGreaterThan(maxSegmentSize);

        String thirdSentence = "This is another short sentence.";
        assertThat(thirdSentence).hasSizeLessThan(maxSegmentSize);

        Document document = document(format(" %s  %s  %s ", firstSentence, secondSentence, thirdSentence));

        SentenceSplitter splitter = new SentenceSplitter(maxSegmentSize);

        List<TextSegment> segments = splitter.split(document);

        assertThat(segments).containsExactly(
                textSegment(firstSentence),
                textSegment("This is a very long sentence that does"),
                textSegment("not fit into segment."),
                textSegment(thirdSentence)
        );
    }
}