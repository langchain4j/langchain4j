package dev.langchain4j.retriever;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;

import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Interface for retrieving relevant items.
 * This class is deprecated, use {@link ContentRetriever} instead.
 *
 * @param <T> the type of the items.
 */
@Deprecated
public interface Retriever<T> {

    /**
     * Find relevant items for the given text.
     *
     * @param text the text to search for.
     * @return the list of relevant items.
     */
    List<T> findRelevant(String text);

    /**
     * Find relevant items for the given text and memoryId.
     *
     * <p>Default implementation throws an exception.
     *
     * @param memoryId the memoryId to search for.
     * @param text     the text to search for.
     * @return the list of relevant items.
     */
    default List<T> findRelevant(
            @SuppressWarnings("unused") Object memoryId,
            @SuppressWarnings("unused") String text) {
        throw new RuntimeException("Not implemented");
    }

    default ContentRetriever toContentRetriever() {
        return (query) -> {
            List<TextSegment> relevant = (List<TextSegment>) findRelevant(query.text());
            return relevant.stream()
                    .map(Content::from)
                    .collect(toList());
        };
    }
}
