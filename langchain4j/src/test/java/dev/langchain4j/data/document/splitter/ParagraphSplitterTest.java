package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ParagraphSplitterTest {

    ParagraphSplitter splitter = new ParagraphSplitter();

    @ParameterizedTest
    @ValueSource(strings = {
            "first\r\rsecond\r\rthird\r\rcr\r",
            "first\n\nsecond\n\nthird\n\nlf\n",
            "first\r\n\r\nsecond\r\n\r\nthird\r\n\r\ncrlf\r\n"
    })
    void test_split_by_paragraph(String text) {
        Document document = Document.from(text);

        List<TextSegment> segments = splitter.split(document);

        assertEquals(4, segments.size());
        assertEquals("first", segments.get(0).text());
        assertEquals("second", segments.get(1).text());
        assertEquals("third", segments.get(2).text());
    }
}
