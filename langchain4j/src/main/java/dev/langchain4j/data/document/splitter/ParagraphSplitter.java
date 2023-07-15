package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.document.DocumentSplitter;

import java.util.List;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

public class ParagraphSplitter implements DocumentSplitter {

    @Override
    public List<TextSegment> split(Document document) {
        String text = document.text();
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Document text should not be null or empty");
        }

        String[] paragraphs = text.split("\\R\\R");

        return stream(paragraphs)
                .map(paragraph -> TextSegment.from(paragraph.trim(), document.metadata()))
                .collect(toList());
    }
}
