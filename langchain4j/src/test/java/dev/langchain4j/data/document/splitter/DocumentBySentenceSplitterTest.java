package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.langchain4j.data.document.Metadata.metadata;
import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentBySentenceSplitterTest {

    @Test
    void should_split_into_segments_with_one_sentence_per_segment() {

        int maxSegmentSize = 30;

        String firstSentence = "This is a first sentence.";
        assertThat(firstSentence).hasSizeLessThan(maxSegmentSize);

        String secondSentence = "This is a second sentence.";
        assertThat(secondSentence).hasSizeLessThan(maxSegmentSize);

        assertThat(firstSentence + " " + secondSentence).hasSizeGreaterThan(maxSegmentSize);

        Document document = Document.from(
                format(" %s  %s ", firstSentence, secondSentence),
                metadata("document", "0")
        );

        DocumentSplitter splitter = new DocumentBySentenceSplitter(maxSegmentSize, 0);

        List<TextSegment> segments = splitter.split(document);

        segments.forEach(segment ->
                assertThat(segment.text().length()).isLessThanOrEqualTo(maxSegmentSize));
        assertThat(segments).containsExactly(
                textSegment(firstSentence, metadata("index", "0").put("document", "0")),
                textSegment(secondSentence, metadata("index", "1").put("document", "0"))
        );
    }

    @Test
    void should_split_into_segments_with_multiple_sentences_per_segment() {

        int maxSegmentSize = 60;

        String firstSentence = "This is a first sentence.";
        String secondSentence = "This is a second sentence.";
        assertThat(firstSentence + " " + secondSentence).hasSizeLessThan(maxSegmentSize);

        String thirdSentence = "This is a third sentence.";
        assertThat(firstSentence + " " + secondSentence + " " + thirdSentence)
                .hasSizeGreaterThan(maxSegmentSize);

        Document document = Document.from(
                format(" %s  %s  %s ", firstSentence, secondSentence, thirdSentence),
                metadata("document", "0")
        );

        DocumentSplitter splitter = new DocumentBySentenceSplitter(maxSegmentSize, 0);

        List<TextSegment> segments = splitter.split(document);

        segments.forEach(segment ->
                assertThat(segment.text().length()).isLessThanOrEqualTo(maxSegmentSize));
        assertThat(segments).containsExactly(
                textSegment(firstSentence + " " + secondSentence, metadata("index", "0").put("document", "0")),
                textSegment(thirdSentence, metadata("index", "1").put("document", "0"))
        );
    }

    @Test
    void should_split_sentence_if_it_does_not_fit_into_segment() {

        int maxSegmentSize = 40;

        String firstSentence = "This is a short sentence.";
        assertThat(firstSentence).hasSizeLessThan(maxSegmentSize);

        String secondSentence = "This is a very long sentence that does not fit into segment.";
        assertThat(secondSentence).hasSizeGreaterThan(maxSegmentSize);

        String thirdSentence = "This is another short sentence.";
        assertThat(thirdSentence).hasSizeLessThan(maxSegmentSize);

        Document document = Document.from(
                format(" %s  %s  %s ", firstSentence, secondSentence, thirdSentence),
                metadata("document", "0")
        );

        DocumentSplitter splitter = new DocumentBySentenceSplitter(maxSegmentSize, 0);

        List<TextSegment> segments = splitter.split(document);

        segments.forEach(segment ->
                assertThat(segment.text().length()).isLessThanOrEqualTo(maxSegmentSize));
        assertThat(segments).containsExactly(
                textSegment(firstSentence, metadata("index", "0").put("document", "0")),
                textSegment("This is a very long sentence that does", metadata("index", "1").put("document", "0")),
                textSegment("not fit into segment.", metadata("index", "2").put("document", "0")),
                textSegment(thirdSentence, metadata("index", "3").put("document", "0"))
        );
    }

    @Test
    void should_split_sample_text() {

        String s1 = "In a sleepy hamlet, where the trees towered high, there lived a young boy named Elias.";
        String s2 = "He loved exploring.";

        String s3 = "Fields of gold stretched as far as the eye could see, punctuated by tiny blossoms.";
        String s4 = "The wind whispered.";

        String s5p1 = "Sometimes, it would carry fragrances from the neighboring towns, which included chocolate, " +
                "freshly baked bread, and the salty tang of";

        String s5p2 = "the sea.";

        String s6 = "In the middle of the town, a single lamppost stood.";

        String s7 = "Cats lounged beneath it, stretching languidly in the dappled sunlight.";

        String s8 = "Elias had a dream: to build a flying machine.";
        String s9 = "Some days, it felt impossible.";

        String s10 = "Yet, every evening, he would pull out his sketches, tinkering and toiling away.";

        String s11 = "There was a resilience in his spirit.";
        String s12 = "Birds often stopped to watch.";
        String s13 = "Curiosity is the spark of invention.";
        String s14 = "He believed.";

        String s15 = "And one day, with the town gathered around him, Elias soared.";
        String s16 = "The horizon awaited.";
        String s17 = "Life is full of surprises.";

        String s18 = "Embrace them.";

        Document document = Document.from(
                format(
                        "%s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s %s",
                        s1, s2, s3, s4, s5p1, s5p2, s6, s7, s8, s9, s10, s11, s12, s13, s14, s15, s16, s17, s18
                ),
                metadata("document", "0")
        );

        int maxSegmentSize = 26;
        OpenAiTokenizer tokenizer = new OpenAiTokenizer(GPT_3_5_TURBO);
        DocumentSplitter splitter = new DocumentBySentenceSplitter(maxSegmentSize, 0, tokenizer);

        List<TextSegment> segments = splitter.split(document);

        segments.forEach(segment ->
                assertThat(tokenizer.estimateTokenCountInText(segment.text())).isLessThanOrEqualTo(maxSegmentSize));
        assertThat(segments).containsExactly(
                textSegment(s1 + " " + s2, metadata("index", "0").put("document", "0")),
                textSegment(s3 + " " + s4, metadata("index", "1").put("document", "0")),
                textSegment(s5p1, metadata("index", "2").put("document", "0")),
                textSegment(s5p2, metadata("index", "3").put("document", "0")),
                textSegment(s6, metadata("index", "4").put("document", "0")),
                textSegment(s7, metadata("index", "5").put("document", "0")),
                textSegment(s8 + " " + s9, metadata("index", "6").put("document", "0")),
                textSegment(s10, metadata("index", "7").put("document", "0")),
                textSegment(s11 + " " + s12 + " " + s13 + " " + s14, metadata("index", "8").put("document", "0")),
                textSegment(s15 + " " + s16 + " " + s17, metadata("index", "9").put("document", "0")),
                textSegment(s18, metadata("index", "10").put("document", "0"))
        );
    }
}