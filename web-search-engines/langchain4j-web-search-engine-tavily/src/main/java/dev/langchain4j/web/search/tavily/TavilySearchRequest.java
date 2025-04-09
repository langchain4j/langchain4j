package dev.langchain4j.web.search.tavily;

import java.util.List;

class TavilySearchRequest {

    private String apiKey;
    private String query;
    private String searchDepth;
    private Boolean includeAnswer;
    private Boolean includeRawContent;
    private Integer maxResults;
    private List<String> includeDomains;
    private List<String> excludeDomains;

    TavilySearchRequest(String apiKey, String query, String searchDepth, Boolean includeAnswer, Boolean includeRawContent, Integer maxResults, List<String> includeDomains, List<String> excludeDomains) {
        this.apiKey = apiKey;
        this.query = query;
        this.searchDepth = searchDepth;
        this.includeAnswer = includeAnswer;
        this.includeRawContent = includeRawContent;
        this.maxResults = maxResults;
        this.includeDomains = includeDomains;
        this.excludeDomains = excludeDomains;
    }

    public static TavilySearchRequestBuilder builder() {
        return new TavilySearchRequestBuilder();
    }

    public String getApiKey() {
        return this.apiKey;
    }

    public String getQuery() {
        return this.query;
    }

    public String getSearchDepth() {
        return this.searchDepth;
    }

    public Boolean getIncludeAnswer() {
        return this.includeAnswer;
    }

    public Boolean getIncludeRawContent() {
        return this.includeRawContent;
    }

    public Integer getMaxResults() {
        return this.maxResults;
    }

    public List<String> getIncludeDomains() {
        return this.includeDomains;
    }

    public List<String> getExcludeDomains() {
        return this.excludeDomains;
    }

    public static class TavilySearchRequestBuilder {
        private String apiKey;
        private String query;
        private String searchDepth;
        private Boolean includeAnswer;
        private Boolean includeRawContent;
        private Integer maxResults;
        private List<String> includeDomains;
        private List<String> excludeDomains;

        TavilySearchRequestBuilder() {
        }

        public TavilySearchRequestBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public TavilySearchRequestBuilder query(String query) {
            this.query = query;
            return this;
        }

        public TavilySearchRequestBuilder searchDepth(String searchDepth) {
            this.searchDepth = searchDepth;
            return this;
        }

        public TavilySearchRequestBuilder includeAnswer(Boolean includeAnswer) {
            this.includeAnswer = includeAnswer;
            return this;
        }

        public TavilySearchRequestBuilder includeRawContent(Boolean includeRawContent) {
            this.includeRawContent = includeRawContent;
            return this;
        }

        public TavilySearchRequestBuilder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public TavilySearchRequestBuilder includeDomains(List<String> includeDomains) {
            this.includeDomains = includeDomains;
            return this;
        }

        public TavilySearchRequestBuilder excludeDomains(List<String> excludeDomains) {
            this.excludeDomains = excludeDomains;
            return this;
        }

        public TavilySearchRequest build() {
            return new TavilySearchRequest(this.apiKey, this.query, this.searchDepth, this.includeAnswer, this.includeRawContent, this.maxResults, this.includeDomains, this.excludeDomains);
        }

        public String toString() {
            return "TavilySearchRequest.TavilySearchRequestBuilder(apiKey=" + this.apiKey + ", query=" + this.query + ", searchDepth=" + this.searchDepth + ", includeAnswer=" + this.includeAnswer + ", includeRawContent=" + this.includeRawContent + ", maxResults=" + this.maxResults + ", includeDomains=" + this.includeDomains + ", excludeDomains=" + this.excludeDomains + ")";
        }
    }
}
