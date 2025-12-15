package dev.langchain4j.web.search.tavily;

class TavilySearchResult {

    private String title;
    private String url;
    private String content;
    private String rawContent;
    private Double score;

    public TavilySearchResult(String title, String url, String content, String rawContent, Double score) {
        this.title = title;
        this.url = url;
        this.content = content;
        this.rawContent = rawContent;
        this.score = score;
    }

    public TavilySearchResult() {
    }

    public static TavilySearchResultBuilder builder() {
        return new TavilySearchResultBuilder();
    }

    public String getTitle() {
        return this.title;
    }

    public String getUrl() {
        return this.url;
    }

    public String getContent() {
        return this.content;
    }

    public String getRawContent() {
        return this.rawContent;
    }

    public Double getScore() {
        return this.score;
    }

    public static class TavilySearchResultBuilder {
        private String title;
        private String url;
        private String content;
        private String rawContent;
        private Double score;

        TavilySearchResultBuilder() {
        }

        public TavilySearchResultBuilder title(String title) {
            this.title = title;
            return this;
        }

        public TavilySearchResultBuilder url(String url) {
            this.url = url;
            return this;
        }

        public TavilySearchResultBuilder content(String content) {
            this.content = content;
            return this;
        }

        public TavilySearchResultBuilder rawContent(String rawContent) {
            this.rawContent = rawContent;
            return this;
        }

        public TavilySearchResultBuilder score(Double score) {
            this.score = score;
            return this;
        }

        public TavilySearchResult build() {
            return new TavilySearchResult(this.title, this.url, this.content, this.rawContent, this.score);
        }

        public String toString() {
            return "TavilySearchResult.TavilySearchResultBuilder(title=" + this.title + ", url=" + this.url + ", content=" + this.content + ", rawContent=" + this.rawContent + ", score=" + this.score + ")";
        }
    }
}
