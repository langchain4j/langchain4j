package dev.langchain4j.web.search.brave;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

class BraveWebSearchEngine implements WebSearchEngine {


    private static final String DEFAULT_BASE_URL = "https://api.search.brave.com/res/v1/web/search";

    private final String query;
    private final  String apiKey;
    private final Integer count;
    private final String safeSearch;
    private final String resultFilter;
    private final String freshness;

    public BraveWebSearchEngine(String query,
                                 String apiKey,
                                 Integer count,
                                 String safeSearch,
                                 String resultFilter,
                                 String freshness) {
        this.
        this.query = query;
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
        this.count = count;
        this.safeSearch = safeSearch;
        this.resultFilter = resultFilter;
        this.freshness = freshness;
    }

    @Override
    public WebSearchResults search(final String query) {
        return WebSearchEngine.super.search(query);
    }

    @Override
    public WebSearchResults search(final WebSearchRequest webSearchRequest) {
        return null;
    }
}
