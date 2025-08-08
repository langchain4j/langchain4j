package dev.langchain4j.rag.content.retriever.azure.cosmos.nosql;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.Objects;

public class FullTextContains implements Filter {
    private final String key;
    private final String searchTerm;

    public FullTextContains(String key, String searchTerm) {
        this.key = ensureNotBlank(key, "key");
        this.searchTerm = ensureNotNull(searchTerm, "searchTerm with key '" + key + "'");
    }

    public String key() {
        return key;
    }

    public String searchTerm() {
        return searchTerm;
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
            // Simple contains check for testing - actual implementation depends on the
            // store
            return str.toLowerCase().contains(searchTerm.toLowerCase());
        }

        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FullTextContains that = (FullTextContains) o;
        return Objects.equals(key, that.key) && Objects.equals(searchTerm, that.searchTerm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, searchTerm);
    }
}
