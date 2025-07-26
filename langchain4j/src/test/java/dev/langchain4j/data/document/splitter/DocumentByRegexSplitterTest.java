package dev.langchain4j.data.document.splitter;

import static dev.langchain4j.data.document.Metadata.metadata;
import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.MetadataKeys;
import dev.langchain4j.data.segment.TextSegment;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentByRegexSplitterTest {

    @ParameterizedTest
    @ValueSource(strings = {" ", ",", "\n", "\n\n"})
    void should_split_by(String separator) {

        String text = format("one%stwo%sthree", separator, separator);
        Document document = Document.from(text, metadata("document", "0"));

        int maxSegmentSize = 5;
        DocumentSplitter splitter = new DocumentByRegexSplitter(separator, separator, maxSegmentSize, 0);

        List<TextSegment> segments = splitter.split(document);

        segments.forEach(segment -> assertThat(segment.text().length()).isLessThanOrEqualTo(maxSegmentSize));
        assertThat(segments)
                .containsExactly(
                        textSegment("one", metadata(MetadataKeys.INDEX, "0").put("document", "0")),
                        textSegment("two", metadata(MetadataKeys.INDEX, "1").put("document", "0")),
                        textSegment("three", metadata(MetadataKeys.INDEX, "2").put("document", "0")));
    }

    @Test
    void should_fit_multiple_parts_into_the_same_segment() {

        Document document = Document.from("one two three", metadata("document", "0"));

        int maxSegmentSize = 10;
        DocumentSplitter splitter = new DocumentByRegexSplitter(" ", "\n", maxSegmentSize, 0);

        List<TextSegment> segments = splitter.split(document);

        segments.forEach(segment -> assertThat(segment.text().length()).isLessThanOrEqualTo(maxSegmentSize));
        assertThat(segments)
                .containsExactly(
                        textSegment(
                                "one\ntwo", metadata(MetadataKeys.INDEX, "0").put("document", "0")),
                        textSegment("three", metadata(MetadataKeys.INDEX, "1").put("document", "0")));
    }

    @Test
    void should_split_part_into_sub_parts_if_it_does_not_fit_into_segment() {

        Document document = Document.from(
                "This is a first line.\nThis is a second line.\n\nThis is a third line.", metadata("document", "0"));

        int maxSegmentSize = 15;
        DocumentSplitter subSplitter = new DocumentByWordSplitter(maxSegmentSize, 0);
        DocumentSplitter splitter = new DocumentByRegexSplitter("\n", "\n", maxSegmentSize, 0, subSplitter);

        List<TextSegment> segments = splitter.split(document);

        segments.forEach(segment -> assertThat(segment.text().length()).isLessThanOrEqualTo(maxSegmentSize));
        assertThat(segments)
                .containsExactly(
                        textSegment(
                                "This is a first",
                                metadata(MetadataKeys.INDEX, "0").put("document", "0")),
                        textSegment("line.", metadata(MetadataKeys.INDEX, "1").put("document", "0")),
                        textSegment(
                                "This is a", metadata(MetadataKeys.INDEX, "2").put("document", "0")),
                        textSegment(
                                "second line.",
                                metadata(MetadataKeys.INDEX, "3").put("document", "0")),
                        textSegment(
                                "This is a third",
                                metadata(MetadataKeys.INDEX, "4").put("document", "0")),
                        textSegment("line.", metadata(MetadataKeys.INDEX, "5").put("document", "0")));
    }
}
