package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static dev.langchain4j.data.document.Metadata.metadata;
import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

class DocumentByParagraphSplitterTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "\n\n",
            "\n \n",
            " \n\n",
            "\n\n ",
            " \n \n ",
            "\r\n\r\n"
    })
    void should_split_into_segments_with_one_paragraph_per_segment(String separator) {

        int maxSegmentSize = 30;

        String firstParagraph = "This is a first paragraph.";
        assertThat(firstParagraph).hasSizeLessThan(maxSegmentSize);

        String secondParagraph = "This is a second paragraph.";
        assertThat(secondParagraph).hasSizeLessThan(maxSegmentSize);

        assertThat(firstParagraph + separator + secondParagraph).hasSizeGreaterThan(maxSegmentSize);

        Document document = Document.from(
                format(" %s %s %s ", firstParagraph, separator, secondParagraph),
                metadata("document", "0")
        );

        DocumentSplitter splitter = new DocumentByParagraphSplitter(maxSegmentSize, 0);

        List<TextSegment> segments = splitter.split(document);

        segments.forEach(segment ->
                assertThat(segment.text().length()).isLessThanOrEqualTo(maxSegmentSize));
        assertThat(segments).containsExactly(
                textSegment(firstParagraph, metadata("index", "0").put("document", "0")),
                textSegment(secondParagraph, metadata("index", "1").put("document", "0"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\n\n",
            "\n \n",
            " \n\n",
            "\n\n ",
            " \n \n ",
            "\r\n\r\n"
    })
    void should_split_into_segments_with_multiple_paragraphs_per_segment(String separator) {

        int maxSegmentSize = 60;

        String firstParagraph = "This is a first paragraph.";
        String secondParagraph = "This is a second paragraph.";
        assertThat(firstParagraph + secondParagraph).hasSizeLessThan(maxSegmentSize);

        String thirdParagraph = "This is a third paragraph.";
        assertThat(thirdParagraph).hasSizeLessThan(maxSegmentSize);

        assertThat(firstParagraph + secondParagraph + thirdParagraph)
                .hasSizeGreaterThan(maxSegmentSize);

        Document document = Document.from(
                format(" %s %s %s %s %s ", firstParagraph, separator, secondParagraph, separator, thirdParagraph),
                metadata("document", "0")
        );

        DocumentSplitter splitter = new DocumentByParagraphSplitter(maxSegmentSize, 0);

        List<TextSegment> segments = splitter.split(document);

        segments.forEach(segment ->
                assertThat(segment.text().length()).isLessThanOrEqualTo(maxSegmentSize));
        assertThat(segments).containsExactly(
                textSegment(firstParagraph + "\n\n" + secondParagraph, metadata("index", "0").put("document", "0")),
                textSegment(thirdParagraph, metadata("index", "1").put("document", "0"))
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "\n\n",
            "\n \n",
            " \n\n",
            "\n\n ",
            " \n \n ",
            "\r\n\r\n"
    })
    void should_split_paragraph_into_sentences_if_it_does_not_fit_into_segment(String separator) {

        int maxSegmentSize = 50;

        String firstParagraph = "This is a first paragraph.";
        assertThat(firstParagraph).hasSizeLessThan(maxSegmentSize);

        String firstSentenceOfSecondParagraph = "This is a fist sentence of a second paragraph.";
        assertThat(firstSentenceOfSecondParagraph).hasSizeLessThan(maxSegmentSize);

        String secondSentenceOfSecondParagraph = "This is a second sentence of a second paragraph.";
        assertThat(secondSentenceOfSecondParagraph).hasSizeLessThan(maxSegmentSize);

        String secondParagraph = firstSentenceOfSecondParagraph + " " + secondSentenceOfSecondParagraph;
        assertThat(secondParagraph).hasSizeGreaterThan(maxSegmentSize);

        String thirdParagraph = "This is a third paragraph.";
        assertThat(thirdParagraph).hasSizeLessThan(maxSegmentSize);

        Document document = Document.from(
                format(" %s %s %s %s %s ", firstParagraph, separator, secondParagraph, separator, thirdParagraph),
                metadata("document", "0")
        );

        DocumentSplitter splitter = new DocumentByParagraphSplitter(maxSegmentSize, 0);

        List<TextSegment> segments = splitter.split(document);

        segments.forEach(segment ->
                assertThat(segment.text().length()).isLessThanOrEqualTo(maxSegmentSize));
        assertThat(segments).containsExactly(
                textSegment(firstParagraph, metadata("index", "0").put("document", "0")),
                textSegment(firstSentenceOfSecondParagraph, metadata("index", "1").put("document", "0")),
                textSegment(secondSentenceOfSecondParagraph, metadata("index", "2").put("document", "0")),
                textSegment(thirdParagraph, metadata("index", "3").put("document", "0"))
        );
    }

    @Test
    void should_split_sample_text_containing_multiple_paragraphs() {

        int maxSegmentSize = 65;
        Tokenizer tokenizer = new OpenAiTokenizer(GPT_3_5_TURBO);

        String p1 = "In a small town nestled between two vast mountains, there was a shop unlike any other. " +
                "A unique haven. " +
                "Visitors would often comment on its peculiar charm, always slightly different from what they " +
                "remembered on their previous visits. " +
                "The store stood as a testament to the passage of time and the ever-changing landscape of tales.";
        assertThat(tokenizer.estimateTokenCountInText(p1)).isEqualTo(62);

        String p2p1 = "Upon entering, the first thing to strike you was the enormity of it all. " +
                "Every inch of space was occupied with books. " +
                "Some stood tall and regal on the highest shelves, looking as if they had witnessed epochs come and go. " +
                "They were leather-bound, with pages yellowed by age.";
        assertThat(tokenizer.estimateTokenCountInText(p2p1)).isEqualTo(60);
        String p2p2 = "Others, smaller and brightly adorned, were reminiscent of summer days and childhood laughter. " +
                "But these physical objects were mere vessels. " +
                "It was the stories inside that held power.";
        assertThat(tokenizer.estimateTokenCountInText(p2p2)).isEqualTo(33);

        String p3 = "Mrs. Jenkins ran the shop. " +
                "A mystery in her own right. " +
                "Her silver hair cascaded like a waterfall, and her eyes seemed to see more than most. " +
                "With just a glance, she'd find the perfect story for you.";
        assertThat(tokenizer.estimateTokenCountInText(p3)).isEqualTo(47);

        String p4p1 = "One wet afternoon, Eli entered. " +
                "He was just a boy, lost in the vastness of the store. " +
                "Between the aisles, his small fingers danced on the spines of books, feeling the heartbeat of " +
                "countless tales. " +
                "Then, a simple brown-covered book whispered to him.";
        assertThat(tokenizer.estimateTokenCountInText(p4p1)).isEqualTo(56);
        String p4p2 = "Without grandeur or pretense, it beckoned. " +
                "And he listened.";
        assertThat(tokenizer.estimateTokenCountInText(p4p2)).isEqualTo(15);

        String p5 = "He read. " +
                "And read. " +
                "The world around him melted.";
        assertThat(tokenizer.estimateTokenCountInText(p5)).isEqualTo(12);

        String p6 = "When Mrs. Jenkins approached, night had fallen. " +
                "She gently remarked, \"Books have a way of finding their reader.\" " +
                "Eli simply nodded, understanding the profound truth in her words.";
        assertThat(tokenizer.estimateTokenCountInText(p6)).isEqualTo(36);

        String p7 = "Some places and stories remain etched in our souls, offering lessons and moments of sheer wonder. " +
                "They defy definition.";
        assertThat(tokenizer.estimateTokenCountInText(p7)).isEqualTo(23);

        Document document = Document.from(
                format("%s\n\n%s %s\n\n%s\n\n%s %s\n\n%s\n\n%s\n\n%s", p1, p2p1, p2p2, p3, p4p1, p4p2, p5, p6, p7),
                metadata("document", "0")
        );

        DocumentSplitter splitter = new DocumentByParagraphSplitter(maxSegmentSize, 0, tokenizer);

        List<TextSegment> segments = splitter.split(document);

        segments.forEach(segment ->
                assertThat(tokenizer.estimateTokenCountInText(segment.text())).isLessThanOrEqualTo(maxSegmentSize));
        assertThat(segments).containsExactly(
                textSegment(p1, metadata("index", "0").put("document", "0")),
                textSegment(p2p1, metadata("index", "1").put("document", "0")),
                textSegment(p2p2, metadata("index", "2").put("document", "0")),
                textSegment(p3, metadata("index", "3").put("document", "0")),
                textSegment(p4p1, metadata("index", "4").put("document", "0")),
                textSegment(p4p2, metadata("index", "5").put("document", "0")),
                textSegment(p5 + "\n\n" + p6, metadata("index", "6").put("document", "0")),
                textSegment(p7, metadata("index", "7").put("document", "0"))
        );
    }

    @Test
    void should_split_sample_text_containing_multiple_paragraphs_with_overlap() {

        int maxSegmentSize = 65;
        int maxOverlapSize = 15;
        Tokenizer tokenizer = new OpenAiTokenizer(GPT_3_5_TURBO);

        String s1 = "In a small town nestled between two vast mountains, there was a shop unlike any other.";
        String s2 = "A unique haven.";
        String s3 = "Visitors would often comment on its peculiar charm, always slightly different from what they remembered on their previous visits.";
        String s4 = "The store stood as a testament to the passage of time and the ever-changing landscape of tales.";

        String s5 = "Upon entering, the first thing to strike you was the enormity of it all.";
        String s6 = "Every inch of space was occupied with books.";
        String s7 = "Some stood tall and regal on the highest shelves, looking as if they had witnessed epochs come and go.";
        String s8 = "They were leather-bound, with pages yellowed by age.";
        String s9 = "Others, smaller and brightly adorned, were reminiscent of summer days and childhood laughter.";
        String s10 = "But these physical objects were mere vessels.";
        String s11 = "It was the stories inside that held power.";

        String s12 = "Mrs. Jenkins ran the shop.";
        String s13 = "A mystery in her own right.";
        String s14 = "Her silver hair cascaded like a waterfall, and her eyes seemed to see more than most.";
        String s15 = "With just a glance, she'd find the perfect story for you.";

        String s16 = "One wet afternoon, Eli entered.";
        String s17 = "He was just a boy, lost in the vastness of the store.";
        String s18 = "Between the aisles, his small fingers danced on the spines of books, feeling the heartbeat of countless tales.";
        String s19 = "Then, a simple brown-covered book whispered to him.";
        String s20 = "Without grandeur or pretense, it beckoned.";
        String s21 = "And he listened.";
        String s22 = "He read.";
        String s23 = "And read.";
        String s24 = "The world around him melted.";

        String s25 = "When Mrs. Jenkins approached, night had fallen.";
        String s26 = "She gently remarked, \"Books have a way of finding their reader.\"";
        String s27 = "Eli simply nodded, understanding the profound truth in her words.";
        String s28 = "Some places and stories remain etched in our souls, offering lessons and moments of sheer wonder.";
        String s29 = "They defy definition.";

        Document document = Document.from(
                format("%s %s %s %s\n\n%s %s %s %s %s %s %s\n\n%s %s %s %s\n\n%s %s %s %s %s %s %s %s %s\n\n%s %s %s %s %s",
                        s1, s2, s3, s4,
                        s5, s6, s7, s8, s9, s10, s11,
                        s12, s13, s14, s15,
                        s16, s17, s18, s19, s20, s21, s22, s23, s24,
                        s25, s26, s27, s28, s29
                ),
                metadata("document", "0")
        );

        DocumentSplitter splitter = new DocumentByParagraphSplitter(maxSegmentSize, maxOverlapSize, tokenizer);

        List<TextSegment> segments = splitter.split(document);

        segments.forEach(segment ->
                assertThat(tokenizer.estimateTokenCountInText(segment.text())).isLessThanOrEqualTo(maxSegmentSize));
        assertThat(segments).containsExactly(
                textSegment(format("%s %s %s %s", s1, s2, s3, s4), metadata("index", "0").put("document", "0")),
                textSegment(format("%s %s %s %s", s5, s6, s7, s8), metadata("index", "1").put("document", "0")),
                textSegment(format("%s %s %s %s", s8, s9, s10, s11), metadata("index", "2").put("document", "0")),
                textSegment(format("%s\n\n%s %s %s %s", s11, s12, s13, s14, s15), metadata("index", "3").put("document", "0")),
                textSegment(format("%s %s %s %s", s15, s16, s17, s18), metadata("index", "4").put("document", "0")),
                textSegment(format("%s %s %s %s %s %s", s19, s20, s21, s22, s23, s24), metadata("index", "5").put("document", "0")),
                textSegment(format("%s %s %s %s %s %s", s22, s23, s24, s25, s26, s27), metadata("index", "6").put("document", "0")),
                textSegment(format("%s %s %s", s27, s28, s29), metadata("index", "7").put("document", "0"))
        );
    }

    @Test
    void should_split_sample_text_without_paragraphs() {

        int maxSegmentSize = 100;
        Tokenizer tokenizer = new OpenAiTokenizer(GPT_3_5_TURBO);

        String segment1 = "In a small town nestled between two vast mountains, there was a shop unlike any other. " +
                "A unique haven. " +
                "Visitors would often comment on its peculiar charm, always slightly different from what they " +
                "remembered on their previous visits. " +
                "The store stood as a testament to the passage of time and the ever-changing landscape of tales. " +
                "Upon entering, the first thing to strike you was the enormity of it all. " +
                "Every inch of space was occupied with books.";

        String segment2 = "Some stood tall and regal on the highest shelves, " +
                "looking as if they had witnessed epochs come and go. " +
                "They were leather-bound, with pages yellowed by age. " +
                "Others, smaller and brightly adorned, were reminiscent of summer days and childhood laughter. " +
                "But these physical objects were mere vessels. " +
                "It was the stories inside that held power. " +
                "Mrs. Jenkins ran the shop. " +
                "A mystery in her own right.";

        String segment3 = "Her silver hair cascaded like a waterfall, and her eyes seemed to see more than most. " +
                "With just a glance, she'd find the perfect story for you. " +
                "One wet afternoon, Eli entered. " +
                "He was just a boy, lost in the vastness of the store. " +
                "Between the aisles, his small fingers danced on the spines of books, feeling the heartbeat of " +
                "countless tales. " +
                "Then, a simple brown-covered book whispered to him.";

        String segment4 = "Without grandeur or pretense, it beckoned. " +
                "And he listened. " +
                "He read. " +
                "And read. " +
                "The world around him melted. " +
                "When Mrs. Jenkins approached, night had fallen. " +
                "She gently remarked, \"Books have a way of finding their reader.\" " +
                "Eli simply nodded, understanding the profound truth in her words. " +
                "Some places and stories remain etched in our souls, offering lessons and moments of sheer wonder. " +
                "They defy definition.";

        Document document = Document.from(
                format("%s %s %s %s", segment1, segment2, segment3, segment4),
                metadata("document", "0")
        );

        DocumentSplitter splitter = new DocumentByParagraphSplitter(maxSegmentSize, 0, tokenizer);

        List<TextSegment> segments = splitter.split(document);

        segments.forEach(segment ->
                assertThat(tokenizer.estimateTokenCountInText(segment.text())).isLessThanOrEqualTo(maxSegmentSize));
        assertThat(segments).containsExactly(
                textSegment(segment1, metadata("index", "0").put("document", "0")),
                textSegment(segment2, metadata("index", "1").put("document", "0")),
                textSegment(segment3, metadata("index", "2").put("document", "0")),
                textSegment(segment4, metadata("index", "3").put("document", "0"))
        );
    }

    @Test
    void should_split_sample_text_without_paragraphs_with_small_overlap() {

        // given
        int maxSegmentSize = 100;
        int maxOverlapSize = 25;
        Tokenizer tokenizer = new OpenAiTokenizer(GPT_3_5_TURBO);
        DocumentSplitter splitter = new DocumentByParagraphSplitter(maxSegmentSize, maxOverlapSize, tokenizer);

        Document document = Document.from(sentences(0, 28), Metadata.from("document", "0"));

        // when
        List<TextSegment> segments = splitter.split(document);

        // then
        segments.forEach(segment ->
                assertThat(tokenizer.estimateTokenCountInText(segment.text())).isLessThanOrEqualTo(maxSegmentSize));

        assertThat(segments).containsExactly(
                TextSegment.from(sentences(0, 5), Metadata.from("index", "0").put("document", "0")),
                TextSegment.from(sentences(5, 12), Metadata.from("index", "1").put("document", "0")),
                TextSegment.from(sentences(10, 16), Metadata.from("index", "2").put("document", "0")),
                TextSegment.from(sentences(15, 24), Metadata.from("index", "3").put("document", "0")),
                TextSegment.from(sentences(21, 28), Metadata.from("index", "4").put("document", "0"))
        );

        assertThat(tokenizer.estimateTokenCountInText(sentences(5, 5))).isLessThanOrEqualTo(maxOverlapSize);
        assertThat(tokenizer.estimateTokenCountInText(sentences(10, 12))).isLessThanOrEqualTo(maxOverlapSize);
        assertThat(tokenizer.estimateTokenCountInText(sentences(15, 16))).isLessThanOrEqualTo(maxOverlapSize);
        assertThat(tokenizer.estimateTokenCountInText(sentences(21, 24))).isLessThanOrEqualTo(maxOverlapSize);
    }

    @Test
    void should_split_sample_text_without_paragraphs_with_big_overlap() {

        // given
        int maxSegmentSize = 100;
        int maxOverlapSize = 80;
        Tokenizer tokenizer = new OpenAiTokenizer(GPT_3_5_TURBO);
        DocumentSplitter splitter = new DocumentByParagraphSplitter(maxSegmentSize, maxOverlapSize, tokenizer);

        Document document = Document.from(sentences(0, 28), Metadata.from("document", "0"));

        // when
        List<TextSegment> segments = splitter.split(document);

        // then
        segments.forEach(segment ->
                assertThat(tokenizer.estimateTokenCountInText(segment.text())).isLessThanOrEqualTo(maxSegmentSize));

        assertThat(segments).containsExactly(
                TextSegment.from(sentences(0, 5), Metadata.from("index", "0").put("document", "0")),
                TextSegment.from(sentences(1, 6), Metadata.from("index", "1").put("document", "0")),
                TextSegment.from(sentences(3, 8), Metadata.from("index", "2").put("document", "0")),
                // TODO fix chopped "Mrs."
                TextSegment.from(sentences(4, 10) + " Mrs.", Metadata.from("index", "3").put("document", "0")),
                TextSegment.from(sentences(5, 12), Metadata.from("index", "4").put("document", "0")),
                TextSegment.from(sentences(7, 15), Metadata.from("index", "5").put("document", "0")),
                TextSegment.from(sentences(9, 16), Metadata.from("index", "6").put("document", "0")),
                // TODO fix chopped s18
                // TODO splitter should prioritize progressing forward instead of maximizing overlap
                TextSegment.from(sentences(10, 16) + " " + sentences[17].replace(" countless tales.", ""), Metadata.from("index", "7").put("document", "0")),
                // TODO this segment should not be present, there is s14-s19 below
                TextSegment.from(sentences(13, 17), Metadata.from("index", "8").put("document", "0")),
                TextSegment.from(sentences(13, 18), Metadata.from("index", "9").put("document", "0")),
                TextSegment.from(sentences(14, 23), Metadata.from("index", "10").put("document", "0")),
                TextSegment.from(sentences(16, 24), Metadata.from("index", "11").put("document", "0")),
                TextSegment.from(sentences(17, 26), Metadata.from("index", "12").put("document", "0")),
                TextSegment.from(sentences(18, 28), Metadata.from("index", "13").put("document", "0"))
        );

        assertThat(tokenizer.estimateTokenCountInText(sentences(1, 5))).isLessThanOrEqualTo(maxOverlapSize);
        assertThat(tokenizer.estimateTokenCountInText(sentences(3, 6))).isLessThanOrEqualTo(maxOverlapSize);
        assertThat(tokenizer.estimateTokenCountInText(sentences(4, 8))).isLessThanOrEqualTo(maxOverlapSize);
        assertThat(tokenizer.estimateTokenCountInText(sentences(5, 10))).isLessThanOrEqualTo(maxOverlapSize);
        assertThat(tokenizer.estimateTokenCountInText(sentences(7, 12))).isLessThanOrEqualTo(maxOverlapSize);
        assertThat(tokenizer.estimateTokenCountInText(sentences(9, 15))).isLessThanOrEqualTo(maxOverlapSize);
        assertThat(tokenizer.estimateTokenCountInText(sentences(10, 16))).isLessThanOrEqualTo(maxOverlapSize);
        assertThat(tokenizer.estimateTokenCountInText(sentences(13, 16))).isLessThanOrEqualTo(maxOverlapSize);
        assertThat(tokenizer.estimateTokenCountInText(sentences(13, 17))).isLessThanOrEqualTo(maxOverlapSize);
        assertThat(tokenizer.estimateTokenCountInText(sentences(14, 18))).isLessThanOrEqualTo(maxOverlapSize);
        assertThat(tokenizer.estimateTokenCountInText(sentences(16, 23))).isLessThanOrEqualTo(maxOverlapSize);
        assertThat(tokenizer.estimateTokenCountInText(sentences(17, 24))).isLessThanOrEqualTo(maxOverlapSize);
        assertThat(tokenizer.estimateTokenCountInText(sentences(18, 26))).isLessThanOrEqualTo(maxOverlapSize);
    }

    static String[] sentences = {
            "In a small town nestled between two vast mountains, there was a shop unlike any other.",
            "A unique haven.",
            "Visitors would often comment on its peculiar charm, always slightly different from what they remembered on their previous visits.",
            "The store stood as a testament to the passage of time and the ever-changing landscape of tales.",
            "Upon entering, the first thing to strike you was the enormity of it all.",
            "Every inch of space was occupied with books.",
            "Some stood tall and regal on the highest shelves, looking as if they had witnessed epochs come and go.",
            "They were leather-bound, with pages yellowed by age.",
            "Others, smaller and brightly adorned, were reminiscent of summer days and childhood laughter.",
            "But these physical objects were mere vessels.",
            "It was the stories inside that held power.",
            "Mrs. Jenkins ran the shop.",
            "A mystery in her own right.",
            "Her silver hair cascaded like a waterfall, and her eyes seemed to see more than most.",
            "With just a glance, she'd find the perfect story for you.",
            "One wet afternoon, Eli entered.",
            "He was just a boy, lost in the vastness of the store.",
            "Between the aisles, his small fingers danced on the spines of books, feeling the heartbeat of countless tales.",
            "Then, a simple brown-covered book whispered to him.",
            "Without grandeur or pretense, it beckoned.",
            "And he listened.",
            "He read.",
            "And read.",
            "The world around him melted.",
            "When Mrs. Jenkins approached, night had fallen.",
            "She gently remarked, \"Books have a way of finding their reader.\"",
            "Eli simply nodded, understanding the profound truth in her words.",
            "Some places and stories remain etched in our souls, offering lessons and moments of sheer wonder.",
            "They defy definition."
    };

    @Test
    void should_split_text_with_CRLF_line_endings() {

        // given
        Document document = Document.from("Title\r\n\r\nHeader 1\r\nText 1\r\n\r\nHeader 2\r\nText 2");

        DocumentSplitter splitter = new DocumentByParagraphSplitter(7, 0, new OpenAiTokenizer());

        // when
        List<TextSegment> segments = splitter.split(document);

        // then
        assertThat(segments).containsExactly(
                TextSegment.from("Title", Metadata.from("index", "0")),
                TextSegment.from("Header 1\r\nText 1", Metadata.from("index", "1")),
                TextSegment.from("Header 2\r\nText 2", Metadata.from("index", "2"))
        );
    }

    private static String sentences(int fromInclusive, int toInclusive) {
        StringBuilder sb = new StringBuilder();
        for (int i = fromInclusive; i <= toInclusive; i++) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(sentences[i]);
        }
        return sb.toString();
    }
}