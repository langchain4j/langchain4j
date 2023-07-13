package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.List;

import static dev.langchain4j.data.document.Document.document;
import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CharacterSplitterTest {

    @Test
    void should_split_with_overlap() {
        DocumentSplitter splitter = new CharacterSplitter(4, 2);

        List<TextSegment> segments = splitter.split(document("1234567890"));

        assertThat(segments).containsExactly(
                textSegment("1234"),
                textSegment("3456"),
                textSegment("5678"),
                textSegment("7890")
        );
    }

    @Test
    void should_split_without_overlap() {
        DocumentSplitter splitter = new CharacterSplitter(4, 0);

        List<TextSegment> segments = splitter.split(document("1234567890"));

        assertThat(segments).containsExactly(
                textSegment("1234"),
                textSegment("5678"),
                textSegment("90")
        );
    }

    @ParameterizedTest
    @CsvSource({"0,-1", "-1,-1", "-1,0", "0,0", "0,1", "1,-1", "1,1", "1,2"})
    void should_fail_on_invalid_length_or_overlap(int segmentLength, int segmentOverlap) {
        DocumentSplitter splitter = new CharacterSplitter(segmentLength, segmentOverlap);

        assertThatThrownBy(() -> splitter.split(document("any")))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid segmentLength (%s) or segmentOverlap (%s)", segmentLength, segmentOverlap);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testNullCase(String documentText) {
        DocumentSplitter splitter = new CharacterSplitter(4, 2);

        assertThatThrownBy(() -> splitter.split(document(documentText)))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Document text should not be null or empty");
    }
}