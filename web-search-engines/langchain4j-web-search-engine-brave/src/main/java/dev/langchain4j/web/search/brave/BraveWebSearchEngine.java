package dev.langchain4j.web.search.brave;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;

public class BraveWebSearchEngine implements WebSearchEngine {

    private static final String DEFAULT_BASE_URL="https://api.search.brave.com/res/v1/";

    @Override
    public WebSearchResults search(final String query) {
        return WebSearchEngine.super.search(query);
    }

    @Override
    public WebSearchResults search(final WebSearchRequest webSearchRequest) {
        return null;
    }
}
