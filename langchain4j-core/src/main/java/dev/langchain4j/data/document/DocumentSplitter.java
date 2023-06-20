package dev.langchain4j.data.document;

import java.util.List;

public interface DocumentSplitter {

    List<DocumentSegment> split(Document document);
}
