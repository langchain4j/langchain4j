package dev.langchain4j.retriever;

import java.util.List;

/**
 * Interface for retrieving relevant items.
 * @param <T> the type of the items.
 */
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

    /**
     * Find relevant items for the given text and partitions.
     * Currently only milvus is supported.
     *
     * <p>Default implementation throws an exception.
     *
     * @param text          the text to search for.
     * @param partitions    milvus collection partition list.
     * @return  the list of relevant items.
     */
    default List<T> findRelevant(String text, List<String> partitions) {
         throw new RuntimeException("Not implemented");
    }
}
