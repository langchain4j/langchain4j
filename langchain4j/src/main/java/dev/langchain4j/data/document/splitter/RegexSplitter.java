package dev.langchain4j.data.document.splitter;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.document.DocumentSplitter;

import java.util.List;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;

public class RegexSplitter implements DocumentSplitter {

    private final String regex;

    public RegexSplitter(String regex) {
        this.regex = regex;
    }

    @Override
    public List<TextSegment> split(Document document) {
        String text = document.text();
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Document text should not be null or empty");
        }

        String[] segments = text.split(regex);

        return stream(segments)
                .map(segment -> TextSegment.from(segment, document.metadata()))
                .collect(toList());
    }
}
