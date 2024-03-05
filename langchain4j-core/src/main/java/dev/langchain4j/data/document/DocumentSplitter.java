package dev.langchain4j.data.document;

import dev.langchain4j.data.segment.TextSegment;

import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Defines the interface for splitting a document into text segments.
 * This is necessary as LLMs have a limited context window, making it impossible to send the entire document at once.
 * Therefore, the document should first be split into segments, and only the relevant segments should be sent to LLM.
 * <br>
 * {@code DocumentSplitters.recursive()} from a {@code dev.langchain4j:langchain4j} module is a good starting point.
 */
public interface DocumentSplitter {

    /**
     * Splits a single Document into a list of TextSegment objects.
     * The metadata is typically copied from the document and enriched with segment-specific information,
     * such as position in the document, page number, etc.
     *
     * @param document The Document to be split.
     * @return A list of TextSegment objects derived from the input Document.
     */
    List<TextSegment> split(Document document);

    /**
     * Splits a list of Documents into a list of TextSegment objects.
     * This is a convenience method that calls the split method for each Document in the list.
     *
     * @param documents The list of Documents to be split.
     * @return A list of TextSegment objects derived from the input Documents.
     */
    default List<TextSegment> splitAll(List<Document> documents) {
        return documents.stream()
                .flatMap(document -> split(document).stream())
                .collect(toList());
    }
}
