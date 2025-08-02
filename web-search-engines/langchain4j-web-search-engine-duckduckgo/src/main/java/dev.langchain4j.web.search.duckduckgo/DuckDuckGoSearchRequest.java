package dev.langchain4j.web.search.duckduckgo;

class DuckDuckGoSearchRequest {
    private String query;
    private String format;
    private Boolean noHtml;
    private Boolean skipDisambig;
    private String safeSearch;
    private String region;
    private Integer maxResults;

    DuckDuckGoSearchRequest(
            String query,
            String format,
            Boolean noHtml,
            Boolean skipDisambig,
            String safeSearch,
            String region,
            Integer maxResults) {
        this.query = query;
        this.format = format;
        this.noHtml = noHtml;
        this.skipDisambig = skipDisambig;
        this.safeSearch = safeSearch;
        this.region = region;
        this.maxResults = maxResults;
    }

    public static DuckDuckGoSearchRequestBuilder builder() {
        return new DuckDuckGoSearchRequestBuilder();
    }

    public String getQuery() {
        return this.query;
    }

    public String getFormat() {
        return this.format;
    }

    public Boolean getNoHtml() {
        return this.noHtml;
    }

    public Boolean getSkipDisambig() {
        return this.skipDisambig;
    }

    public String getSafeSearch() {
        return this.safeSearch;
    }

    public String getRegion() {
        return this.region;
    }

    public Integer getMaxResults() {
        return this.maxResults;
    }

    public static class DuckDuckGoSearchRequestBuilder {
        private String query;
        private String format;
        private Boolean noHtml;
        private Boolean skipDisambig;
        private String safeSearch;
        private String region;
        private Integer maxResults;

        DuckDuckGoSearchRequestBuilder() {}

        public DuckDuckGoSearchRequestBuilder query(String query) {
            this.query = query;
            return this;
        }

        public DuckDuckGoSearchRequestBuilder format(String format) {
            this.format = format;
            return this;
        }

        public DuckDuckGoSearchRequestBuilder noHtml(Boolean noHtml) {
            this.noHtml = noHtml;
            return this;
        }

        public DuckDuckGoSearchRequestBuilder skipDisambig(Boolean skipDisambig) {
            this.skipDisambig = skipDisambig;
            return this;
        }

        public DuckDuckGoSearchRequestBuilder safeSearch(String safeSearch) {
            this.safeSearch = safeSearch;
            return this;
        }

        public DuckDuckGoSearchRequestBuilder region(String region) {
            this.region = region;
            return this;
        }

        public DuckDuckGoSearchRequestBuilder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public DuckDuckGoSearchRequest build() {
            return new DuckDuckGoSearchRequest(
                    this.query,
                    this.format,
                    this.noHtml,
                    this.skipDisambig,
                    this.safeSearch,
                    this.region,
                    this.maxResults);
        }

        public String toString() {
            return "DuckDuckGoSearchRequest.DuckDuckGoSearchRequestBuilder(query=" + this.query + ", format="
                    + this.format + ", noHtml=" + this.noHtml + ", skipDisambig=" + this.skipDisambig + ", safeSearch="
                    + this.safeSearch + ", region=" + this.region + ", maxResults=" + this.maxResults + ")";
        }
    }
}
