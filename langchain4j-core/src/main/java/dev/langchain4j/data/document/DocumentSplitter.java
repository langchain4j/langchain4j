package dev.langchain4j.data.document;

import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

import static java.util.stream.Collectors.toList;

public interface DocumentSplitter {

    List<TextSegment> split(Document document);

    default List<TextSegment> split(List<Document> documents) {
        return documents.stream()
                .flatMap(document -> split(document).stream())
                .collect(toList());
    }
}
