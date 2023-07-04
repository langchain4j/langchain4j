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
            "test-file-with-paragraphs-cr.txt",
            "test-file-with-paragraphs-crlf.txt",
            "test-file-with-paragraphs-lf.txt"
    })
    void test_split_by_paragraph(String fileName) {
        DocumentLoader loader = DocumentLoader.from(Paths.get("src/test/resources/" + fileName));
        Document document = loader.load();

        List<DocumentSegment> documentSegments = splitter.split(document);

        assertEquals(3, documentSegments.size());
        assertEquals("first", documentSegments.get(0).text());
        assertEquals("second", documentSegments.get(1).text());
        assertEquals("third", documentSegments.get(2).text());
    }
}