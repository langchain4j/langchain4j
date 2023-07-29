package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.data.document.Document.document;
import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

class ParagraphSplitterTest {

    @Test
    void should_split_into_segments_with_one_paragraph_per_segment() {

        int maxSegmentSize = 30;

        String firstParagraph = "This is first paragraph.";
        assertThat(firstParagraph).hasSizeLessThan(maxSegmentSize);

        String secondParagraph = "This is second paragraph.";
        assertThat(secondParagraph).hasSizeLessThan(maxSegmentSize);

        assertThat(firstParagraph + "\n\n" + secondParagraph).hasSizeGreaterThan(maxSegmentSize);

        Document document = document(format(" %s \n \n %s ", firstParagraph, secondParagraph));

        ParagraphSplitter splitter = new ParagraphSplitter(maxSegmentSize);

        List<TextSegment> segments = splitter.split(document);

        assertThat(segments).containsExactly(
                textSegment(firstParagraph),
                textSegment(secondParagraph)
        );
    }

    // TODO with tokenizer

    @Test
    void should_split_into_segments_with_multiple_paragraphs_per_segment() {

        int maxSegmentSize = 60;

        String firstParagraph = "This is first paragraph.";
        String secondParagraph = "This is second paragraph.";
        assertThat(firstParagraph + "\n\n" + secondParagraph).hasSizeLessThan(maxSegmentSize);

        String thirdParagraph = "This is third paragraph.";
        assertThat(firstParagraph + "\n\n" + secondParagraph + "\n\n" + thirdParagraph)
                .hasSizeGreaterThan(maxSegmentSize);

        Document document = document(format(" %s \n \n %s \n \n %s ", firstParagraph, secondParagraph, thirdParagraph));

        ParagraphSplitter splitter = new ParagraphSplitter(maxSegmentSize);

        List<TextSegment> segments = splitter.split(document);

        assertThat(segments).containsExactly(
                textSegment(firstParagraph + "\n\n" + secondParagraph),
                textSegment(thirdParagraph)
        );
    }

    @Test
    void should_split_paragraph_into_sentences_if_it_does_not_fit_into_segment() {

        int maxSegmentSize = 50; // TODO do not put paragraph parts to other segments

        String firstParagraph = "This is first paragraph.";
        assertThat(firstParagraph).hasSizeLessThan(maxSegmentSize);

        String firstSentenceOfSecondParagraph = "This is fist sentence of second paragraph.";
        assertThat(firstSentenceOfSecondParagraph).hasSizeLessThan(maxSegmentSize);

        String secondSentenceOfSecondParagraph = "This is second sentence of second paragraph.";
        assertThat(secondSentenceOfSecondParagraph).hasSizeLessThan(maxSegmentSize);

        String secondParagraph = firstSentenceOfSecondParagraph + " " + secondSentenceOfSecondParagraph;
        assertThat(secondParagraph).hasSizeGreaterThan(maxSegmentSize);

        String thirdParagraph = "This is third paragraph.";
        assertThat(thirdParagraph).hasSizeLessThan(maxSegmentSize);

        Document document = document(format(" %s \n \n %s \n \n %s ", firstParagraph, secondParagraph, thirdParagraph));

        ParagraphSplitter splitter = new ParagraphSplitter(maxSegmentSize);

        List<TextSegment> segments = splitter.split(document);

        assertThat(segments).containsExactly(
                textSegment(firstParagraph),
                textSegment(firstSentenceOfSecondParagraph),
                textSegment(secondSentenceOfSecondParagraph),
                textSegment(thirdParagraph)
        );
    }

    // TODO without newlines. wall of text.
}