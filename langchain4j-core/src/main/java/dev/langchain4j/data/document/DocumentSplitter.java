package dev.langchain4j.data.document;

import java.util.List;

import static java.util.stream.Collectors.toList;

public interface DocumentSplitter {

    List<DocumentSegment> split(Document document);

    default List<DocumentSegment> split(List<Document> documents) {
        return documents.stream()
                .flatMap(document -> split(document).stream())
                .collect(toList());
    }
}
