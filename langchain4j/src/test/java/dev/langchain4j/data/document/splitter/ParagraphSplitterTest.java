package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentLoader;
import dev.langchain4j.data.document.DocumentSegment;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Paths;
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

        List<DocumentSegment> documentSegments = splitter.split(document);

        assertEquals(4, documentSegments.size());
        assertEquals("first", documentSegments.get(0).text());
        assertEquals("second", documentSegments.get(1).text());
        assertEquals("third", documentSegments.get(2).text());
    }
}
