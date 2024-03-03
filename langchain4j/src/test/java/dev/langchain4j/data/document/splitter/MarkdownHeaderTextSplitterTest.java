package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static dev.langchain4j.data.document.Metadata.metadata;
import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static org.assertj.core.api.Assertions.assertThat;


class MarkdownHeaderTextSplitterTest {

    public static final String MARKDOWN_TEXT = "# Header 1\n\nThis is some text under the first header.\n\n# Header 2\n\nThis is some text under the second header.";

    @Test
    void should_fit_multiple_parts_into_the_same_segment() {

        Document document = Document.from(MARKDOWN_TEXT, metadata("document", "0"));
        int maxSegmentSize = 110;
        DocumentSplitter splitter = new DocumentByMarkdownHeaderSplitter(EnumSet.of(MarkdownHeaderLevel.H1), "", maxSegmentSize, 0);
        List<TextSegment> segments = splitter.split(document);
        segments.forEach(segment ->
                assertThat(segment.text().length()).isLessThanOrEqualTo(maxSegmentSize));
        assertThat(segments).containsExactly(
                textSegment(MARKDOWN_TEXT, metadata("index", "0").add("document", "0"))
        );
    }

    @Test
    void should_split_part_into_sub_parts_if_it_does_not_fit_into_segment() {

        Document document = Document.from(MARKDOWN_TEXT, metadata("document", "0"));
        int maxSegmentSize = 15;
        DocumentSplitter subSplitter = new DocumentByWordSplitter(maxSegmentSize, 0);
        DocumentSplitter splitter = new DocumentByMarkdownHeaderSplitter(EnumSet.of(MarkdownHeaderLevel.H1), "\n", maxSegmentSize, 0, subSplitter);

        List<TextSegment> segments = splitter.split(document);

        segments.forEach(segment ->
                assertThat(segment.text().length()).isLessThanOrEqualTo(maxSegmentSize));
        assertThat(segments).containsExactly(
                textSegment("# Header 1 This", metadata("index", "0").add("document", "0")),
                textSegment("is some text", metadata("index", "1").add("document", "0")),
                textSegment("under the first", metadata("index", "2").add("document", "0")),
                textSegment("header.", metadata("index", "3").add("document", "0")),
                textSegment("# Header 2 This", metadata("index", "4").add("document", "0")),
                textSegment("is some text", metadata("index", "5").add("document", "0")),
                textSegment("under the", metadata("index", "6").add("document", "0")),
                textSegment("second header.", metadata("index", "7").add("document", "0"))
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideTestArguments")
    void should_split_according_to_provideTestArguments(String name, String markdownText, Set<MarkdownHeaderLevel> headerLevels, int maxSegmentSize, int expectedSize, List<TextSegment> expectedSegments) {
        Document document = Document.from(markdownText, metadata("document", "0"));
        DocumentSplitter splitter = new DocumentByMarkdownHeaderSplitter(headerLevels, "\n", maxSegmentSize, 0);
        List<TextSegment> segments = splitter.split(document);
        Assertions.assertEquals(expectedSize, segments.size(), "Should split by specified headers");
        segments.forEach(segment ->
                assertThat(segment.text().length()).isLessThanOrEqualTo(maxSegmentSize));
        assertThat(segments).containsExactlyElementsOf(expectedSegments);
    }

    private static Stream<Arguments> provideTestArguments() {
        return Stream.of(
                Arguments.of("Test that it splits by header H1",
                        MARKDOWN_TEXT,
                        EnumSet.of(MarkdownHeaderLevel.H1), 55, 2, Arrays.asList(
                                textSegment("# Header 1\n\nThis is some text under the first header.", metadata("index", "0").add("document", "0")),
                                textSegment("# Header 2\n\nThis is some text under the second header.", metadata("index", "1").add("document", "0")))
                ),
                Arguments.of("Test that it splits by any other header like H2",
                        "# Header 1\n\nThis is some text under the first header.\n## Header 2.1\n\nThis is some text under the second header, which is an H2.\n## Header 2.2\n\nThis is more text under another H2 header.",
                        EnumSet.of(MarkdownHeaderLevel.H2), 100, 3, Arrays.asList(
                                textSegment("# Header 1\n\nThis is some text under the first header.", metadata("index", "0").add("document", "0")),
                                textSegment("## Header 2.1\n\nThis is some text under the second header, which is an H2.", metadata("index", "1").add("document", "0")),
                                textSegment("## Header 2.2\n\nThis is more text under another H2 header.", metadata("index", "2").add("document", "0"))
                        )

                ),
                Arguments.of("Test that it splits by multiple headers at the same time H1 and H2",
                        "# Header 1\n\nThis is some text under the first header.\n## Header 2.1\n\nThis is some text under the second header, which is an H2.\n## Header 2.2\n\nThis is more text under another H2 header.",
                        EnumSet.of(MarkdownHeaderLevel.H1, MarkdownHeaderLevel.H2), 100, 3,
                        Arrays.asList(
                                textSegment("# Header 1\n\nThis is some text under the first header.", metadata("index", "0").add("document", "0")),
                                textSegment("## Header 2.1\n\nThis is some text under the second header, which is an H2.", metadata("index", "1").add("document", "0")),
                                textSegment("## Header 2.2\n\nThis is more text under another H2 header.", metadata("index", "2").add("document", "0"))
                        )
                ),
                Arguments.of("Splits by All Header Levels",
                        "# Header 1\n\nText under header 1.\n## Header 2\n\nText under header 2.\n### Header 3\n\nText under header 3.\n#### Header 4\n\nText under header 4.\n##### Header 5\n\nText under header 5.\n###### Header 6\n\nText under header 6.",
                        EnumSet.allOf(MarkdownHeaderLevel.class), 40, 6,
                        Arrays.asList(
                                textSegment("# Header 1\n\nText under header 1.", metadata("index", "0").add("document", "0")),
                                textSegment("## Header 2\n\nText under header 2.", metadata("index", "1").add("document", "0")),
                                textSegment("### Header 3\n\nText under header 3.", metadata("index", "2").add("document", "0")),
                                textSegment("#### Header 4\n\nText under header 4.", metadata("index", "3").add("document", "0")),
                                textSegment("##### Header 5\n\nText under header 5.", metadata("index", "4").add("document", "0")),
                                textSegment("###### Header 6\n\nText under header 6.", metadata("index", "5").add("document", "0"))
                        )
                )
        );
    }
}
