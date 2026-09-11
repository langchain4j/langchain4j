package dev.langchain4j.web.search.brave;

import java.util.List;

/**
 * Represents a single web result returned by the Brave web search endpoint.
 */
class BraveSearchResult {

    private String title;
    private String url;
    private String description;
    private String pageAge;
    private List<String> extraSnippets;

    public BraveSearchResult() {}

    public String getTitle() {
        return this.title;
    }

    public String getUrl() {
        return this.url;
    }

    public String getDescription() {
        return this.description;
    }

    public String getPageAge() {
        return this.pageAge;
    }

    public List<String> getExtraSnippets() {
        return this.extraSnippets;
    }
}
