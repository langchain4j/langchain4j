package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSegment;
import dev.langchain4j.data.document.DocumentSplitter;

import java.util.List;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

public class ParagraphSplitter implements DocumentSplitter {

    @Override
    public List<DocumentSegment> split(Document document) {
        String text = document.text();
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Document text should not be null or empty");
        }

        String[] paragraphs = text.split("\\R\\R");

        return stream(paragraphs)
                .map(paragraph -> DocumentSegment.from(paragraph.trim(), document.metadata()))
                .collect(toList());
    }
}
