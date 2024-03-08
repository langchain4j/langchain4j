package dev.langchain4j.store.embedding;

import dev.langchain4j.Experimental;

import java.util.List;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Represents a result of a search in an {@link EmbeddingStore}.
 */
@Experimental
public class EmbeddingSearchResult<Embedded> {

    private final List<EmbeddingMatch<Embedded>> matches;

    @Experimental
    public EmbeddingSearchResult(List<EmbeddingMatch<Embedded>> matches) {
        this.matches = ensureNotNull(matches, "matches");
    }

    @Experimental
    public List<EmbeddingMatch<Embedded>> matches() {
        return matches;
    }
}
