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
    private String topic;
    private String timeRange;
    private String startDate;
    private String endDate;
    private Boolean includeImages;
    private Boolean includeImageDescriptions;
    private Boolean includeFavicon;
    private String country;
    private Integer chunksPerSource;
    private Boolean autoParameters;
    private Boolean exactMatch;

    TavilySearchRequest(
            String apiKey,
            String query,
            String searchDepth,
            Boolean includeAnswer,
            Boolean includeRawContent,
            Integer maxResults,
            List<String> includeDomains,
            List<String> excludeDomains,
            String topic,
            String timeRange,
            String startDate,
            String endDate,
            Boolean includeImages,
            Boolean includeImageDescriptions,
            Boolean includeFavicon,
            String country,
            Integer chunksPerSource,
            Boolean autoParameters,
            Boolean exactMatch) {
        this.apiKey = apiKey;
        this.query = query;
        this.searchDepth = searchDepth;
        this.includeAnswer = includeAnswer;
        this.includeRawContent = includeRawContent;
        this.maxResults = maxResults;
        this.includeDomains = includeDomains;
        this.excludeDomains = excludeDomains;
        this.topic = topic;
        this.timeRange = timeRange;
        this.startDate = startDate;
        this.endDate = endDate;
        this.includeImages = includeImages;
        this.includeImageDescriptions = includeImageDescriptions;
        this.includeFavicon = includeFavicon;
        this.country = country;
        this.chunksPerSource = chunksPerSource;
        this.autoParameters = autoParameters;
        this.exactMatch = exactMatch;
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

    public String getTopic() {
        return this.topic;
    }

    public String getTimeRange() {
        return this.timeRange;
    }

    public String getStartDate() {
        return this.startDate;
    }

    public String getEndDate() {
        return this.endDate;
    }

    public Boolean getIncludeImages() {
        return this.includeImages;
    }

    public Boolean getIncludeImageDescriptions() {
        return this.includeImageDescriptions;
    }

    public Boolean getIncludeFavicon() {
        return this.includeFavicon;
    }

    public String getCountry() {
        return this.country;
    }

    public Integer getChunksPerSource() {
        return this.chunksPerSource;
    }

    public Boolean getAutoParameters() {
        return this.autoParameters;
    }

    public Boolean getExactMatch() {
        return this.exactMatch;
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
        private String topic;
        private String timeRange;
        private String startDate;
        private String endDate;
        private Boolean includeImages;
        private Boolean includeImageDescriptions;
        private Boolean includeFavicon;
        private String country;
        private Integer chunksPerSource;
        private Boolean autoParameters;
        private Boolean exactMatch;

        TavilySearchRequestBuilder() {}

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

        public TavilySearchRequestBuilder topic(String topic) {
            this.topic = topic;
            return this;
        }

        public TavilySearchRequestBuilder timeRange(String timeRange) {
            this.timeRange = timeRange;
            return this;
        }

        public TavilySearchRequestBuilder startDate(String startDate) {
            this.startDate = startDate;
            return this;
        }

        public TavilySearchRequestBuilder endDate(String endDate) {
            this.endDate = endDate;
            return this;
        }

        public TavilySearchRequestBuilder includeImages(Boolean includeImages) {
            this.includeImages = includeImages;
            return this;
        }

        public TavilySearchRequestBuilder includeImageDescriptions(Boolean includeImageDescriptions) {
            this.includeImageDescriptions = includeImageDescriptions;
            return this;
        }

        public TavilySearchRequestBuilder includeFavicon(Boolean includeFavicon) {
            this.includeFavicon = includeFavicon;
            return this;
        }

        public TavilySearchRequestBuilder country(String country) {
            this.country = country;
            return this;
        }

        public TavilySearchRequestBuilder chunksPerSource(Integer chunksPerSource) {
            this.chunksPerSource = chunksPerSource;
            return this;
        }

        public TavilySearchRequestBuilder autoParameters(Boolean autoParameters) {
            this.autoParameters = autoParameters;
            return this;
        }

        public TavilySearchRequestBuilder exactMatch(Boolean exactMatch) {
            this.exactMatch = exactMatch;
            return this;
        }

        public TavilySearchRequest build() {
            return new TavilySearchRequest(
                    this.apiKey,
                    this.query,
                    this.searchDepth,
                    this.includeAnswer,
                    this.includeRawContent,
                    this.maxResults,
                    this.includeDomains,
                    this.excludeDomains,
                    this.topic,
                    this.timeRange,
                    this.startDate,
                    this.endDate,
                    this.includeImages,
                    this.includeImageDescriptions,
                    this.includeFavicon,
                    this.country,
                    this.chunksPerSource,
                    this.autoParameters,
                    this.exactMatch);
        }

        public String toString() {
            return "TavilySearchRequest.TavilySearchRequestBuilder(apiKey=" + this.apiKey + ", query=" + this.query
                    + ", searchDepth=" + this.searchDepth + ", includeAnswer=" + this.includeAnswer
                    + ", includeRawContent=" + this.includeRawContent + ", maxResults=" + this.maxResults
                    + ", includeDomains=" + this.includeDomains + ", excludeDomains=" + this.excludeDomains
                    + ", topic=" + this.topic + ", timeRange=" + this.timeRange + ", startDate=" + this.startDate
                    + ", endDate=" + this.endDate + ", includeImages=" + this.includeImages
                    + ", includeImageDescriptions=" + this.includeImageDescriptions + ", includeFavicon="
                    + this.includeFavicon + ", country=" + this.country + ", chunksPerSource=" + this.chunksPerSource
                    + ", autoParameters=" + this.autoParameters + ", exactMatch=" + this.exactMatch + ")";
        }
    }
}
