package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.DocumentSegment;
import dev.langchain4j.data.document.DocumentSplitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.util.List;

import static dev.langchain4j.data.document.Document.document;
import static dev.langchain4j.data.document.DocumentSegment.documentSegment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CharacterSplitterTest {

    @Test
    void should_split_with_overlap() {
        DocumentSplitter splitter = new CharacterSplitter(4, 2);

        List<DocumentSegment> segments = splitter.split(document("1234567890"));

        assertThat(segments).containsExactly(
                documentSegment("1234"),
                documentSegment("3456"),
                documentSegment("5678"),
                documentSegment("7890")
        );
    }

    @Test
    void should_split_without_overlap() {
        DocumentSplitter splitter = new CharacterSplitter(4, 0);

        List<DocumentSegment> segments = splitter.split(document("1234567890"));

        assertThat(segments).containsExactly(
                documentSegment("1234"),
                documentSegment("5678"),
                documentSegment("90")
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