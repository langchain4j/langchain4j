package dev.langchain4j.web.search.brave;

/**
 * Represents the {@code query} section of the Brave web search response,
 * which echoes the query and provides pagination hints.
 */
class BraveQuery {

    private String original;
    private String altered;
    private Boolean moreResultsAvailable;

    public BraveQuery() {}

    public String getOriginal() {
        return this.original;
    }

    public String getAltered() {
        return this.altered;
    }

    public Boolean getMoreResultsAvailable() {
        return this.moreResultsAvailable;
    }
}
