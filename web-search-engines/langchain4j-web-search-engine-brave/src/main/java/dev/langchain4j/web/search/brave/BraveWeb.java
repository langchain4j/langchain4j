package dev.langchain4j.web.search.brave;

import java.util.List;

/**
 * Represents the {@code web} section of the Brave web search response,
 * which contains the ranked web results.
 */
class BraveWeb {

    private List<BraveSearchResult> results;

    public BraveWeb() {}

    public List<BraveSearchResult> getResults() {
        return this.results;
    }
}
