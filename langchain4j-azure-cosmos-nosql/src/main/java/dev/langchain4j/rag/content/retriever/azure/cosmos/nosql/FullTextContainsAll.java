package dev.langchain4j.rag.content.retriever.azure.cosmos.nosql;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * A filter that performs full-text search using FullTextContainsAll function.
 * Searches for documents where the specified field contains all the search terms.
 */
public class FullTextContainsAll implements Filter {

    private final String key;
    private final List<String> searchTerms;

    public FullTextContainsAll(String key, String... searchTerms) {
        this(key, Arrays.asList(searchTerms));
    }

    public FullTextContainsAll(String key, Collection<String> searchTerms) {
        this.key = ensureNotBlank(key, "key");
        this.searchTerms = ensureNotNull(searchTerms, "searchTerms with key '" + key + "'").stream()
                .toList();
        if (this.searchTerms.isEmpty()) {
            throw new IllegalArgumentException("searchTerms cannot be empty");
        }
    }

    public String key() {
        return key;
    }

    public List<String> searchTerms() {
        return searchTerms;
    }

    @Override
    public boolean test(Object object) {
        if (!(object instanceof Metadata metadata)) {
            return false;
        }

        if (!metadata.containsKey(key)) {
            return false;
        }

        Object actualValue = metadata.toMap().get(key);
        if (actualValue instanceof String str) {
            String lowerStr = str.toLowerCase();
            // Check if all search terms are contained in the string
            return searchTerms.stream().allMatch(term -> lowerStr.contains(term.toLowerCase()));
        }

        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FullTextContainsAll that = (FullTextContainsAll) o;
        return Objects.equals(key, that.key) && Objects.equals(searchTerms, that.searchTerms);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, searchTerms);
    }
}
