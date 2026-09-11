package dev.langchain4j.web.search.brave;

import java.util.List;

/**
 * Represents the response of the Brave web search endpoint.
 * <br>
 * Depending on the API version, web results are either nested under the {@code web} key
 * or returned in a top-level {@code results} list; both shapes are supported.
 */
class BraveWebSearchResponse {

    private String type;
    private BraveQuery query;
    private BraveWeb web;
    private List<BraveSearchResult> results;

    public BraveWebSearchResponse() {}

    public String getType() {
        return this.type;
    }

    public BraveQuery getQuery() {
        return this.query;
    }

    public BraveWeb getWeb() {
        return this.web;
    }

    public List<BraveSearchResult> getResults() {
        return this.results;
    }
}
