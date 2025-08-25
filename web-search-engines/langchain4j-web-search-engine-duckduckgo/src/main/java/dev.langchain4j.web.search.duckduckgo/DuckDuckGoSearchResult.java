package dev.langchain4j.web.search.duckduckgo;

class DuckDuckGoSearchResult {

    private String title;
    private String url;
    private String snippet;

    public DuckDuckGoSearchResult(String title, String url, String snippet) {
        this.title = title;
        this.url = url;
        this.snippet = snippet;
    }

    public static DuckDuckGoSearchResultBuilder builder() {
        return new DuckDuckGoSearchResultBuilder();
    }

    public String getTitle() {
        return this.title;
    }

    public String getUrl() {
        return this.url;
    }

    public String getSnippet() {
        return this.snippet;
    }

    public static class DuckDuckGoSearchResultBuilder {
        private String title;
        private String url;
        private String snippet;

        DuckDuckGoSearchResultBuilder() {}

        public DuckDuckGoSearchResultBuilder title(String title) {
            this.title = title;
            return this;
        }

        public DuckDuckGoSearchResultBuilder url(String url) {
            this.url = url;
            return this;
        }

        public DuckDuckGoSearchResultBuilder snippet(String snippet) {
            this.snippet = snippet;
            return this;
        }

        public DuckDuckGoSearchResult build() {
            return new DuckDuckGoSearchResult(this.title, this.url, this.snippet);
        }
    }
}
