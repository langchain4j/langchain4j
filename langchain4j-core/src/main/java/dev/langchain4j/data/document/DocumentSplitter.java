package dev.langchain4j.data.document;

import static java.util.stream.Collectors.toList;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Utils;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
        return documents.stream().flatMap(document -> split(document).stream()).collect(toList());
    }

    /**
     * Splits multiple {@link Document} instances into a list of {@link TextSegment} objects.
     * <p>
     * This is a convenience method that allows callers to pass a variable number of Document arguments
     * (using varargs) instead of explicitly creating a list. Internally, it delegates to the {@link #splitAll(List)}
     * method by converting the varargs array into a {@link List}.
     * <p>
     * For example:
     * <pre>{@code
     * List<TextSegment> segments = documentSplitter.splitAll(doc1, doc2, doc3);
     * }</pre>
     * This is equivalent to:
     * <pre>{@code
     * List<TextSegment> segments = documentSplitter.splitAll(Arrays.asList(doc1, doc2, doc3));
     * }</pre>
     *
     * @param documents One or more {@code Document} instances to be split.
     *                  If no documents are provided, an empty list is returned.
     * @return A list of {@code TextSegment} objects derived from the input documents.
     *         The resulting list is a flat combination of all segments from all input documents.
     * @see #split(Document)
     * @see #splitAll(List)
     */
    default List<TextSegment> splitAll(Document... documents) {
        if (Utils.isNullOrEmpty(documents)) {
            return Collections.emptyList();
        }
        return splitAll(Arrays.asList(documents));
    }
}
