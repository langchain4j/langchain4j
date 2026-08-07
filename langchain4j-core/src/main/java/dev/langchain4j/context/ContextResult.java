package dev.langchain4j.context;

import dev.langchain4j.Experimental;
import dev.langchain4j.rag.content.Content;

import java.util.List;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents the result of context resolution by a {@link ContextManager}.
 * <p>
 * Contains the assembled contextual {@link Content} and an advisory flag
 * indicating whether retrieval should proceed.
 *
 * @see ContextManager
 */
@Experimental
public class ContextResult {

    private final List<Content> contents;
    private final boolean retrievalAdvised;

    private ContextResult(List<Content> contents, boolean retrievalAdvised) {
        this.contents = copy(ensureNotNull(contents, "contents"));
        this.retrievalAdvised = retrievalAdvised;
    }

    /**
     * @return the contextual content
     */
    public List<Content> contents() {
        return contents;
    }

    /**
     * @return {@code true} if retrieval should proceed after context resolution,
     *         {@code false} if context alone is sufficient
     */
    public boolean isRetrievalAdvised() {
        return retrievalAdvised;
    }

    /**
     * Creates a result with context contents and retrieval advised.
     *
     * @param contents the contextual content
     * @return a new {@link ContextResult}
     */
    public static ContextResult from(List<Content> contents) {
        return new ContextResult(contents, true);
    }

    /**
     * Creates a result with context contents and retrieval NOT advised.
     * Use this when the context alone is sufficient to answer the query.
     *
     * @param contents the contextual content
     * @return a new {@link ContextResult} with {@code retrievalAdvised = false}
     */
    public static ContextResult withoutRetrieval(List<Content> contents) {
        return new ContextResult(contents, false);
    }
}
