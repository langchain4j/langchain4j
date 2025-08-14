package dev.langchain4j.web.search.searchapi;

import java.util.HashMap;
import java.util.Map;

class SearchApiWebSearchRequest {

    private final String engine;
    private final String apiKey;
    private final String query;
    private final Map<String, Object> finalOptionalParameters;

    /**
     * @param additionalRequestParameters overrides optionalParameters for matching keys
     */
    SearchApiWebSearchRequest(String engine,
                              String apiKey,
                              String query,
                              Map<String, Object> optionalParameters,
                              Map<String, Object> additionalRequestParameters) {
        this.engine = engine;
        this.apiKey = apiKey;
        this.query = query;
        this.finalOptionalParameters = new HashMap<>(optionalParameters);
        if (additionalRequestParameters != null) {
            finalOptionalParameters.putAll(additionalRequestParameters);
        }
    }

    public static SearchApiWebSearchRequestBuilder builder() {
        return new SearchApiWebSearchRequestBuilder();
    }

    public String getEngine() {
        return this.engine;
    }

    public String getApiKey() {
        return this.apiKey;
    }

    public String getQuery() {
        return this.query;
    }

    public Map<String, Object> getFinalOptionalParameters() {
        return this.finalOptionalParameters;
    }

    public static class SearchApiWebSearchRequestBuilder {
        private String engine;
        private String apiKey;
        private String query;
        private Map<String, Object> optionalParameters;
        private Map<String, Object> additionalRequestParameters;

        SearchApiWebSearchRequestBuilder() {
        }

        public SearchApiWebSearchRequestBuilder engine(String engine) {
            this.engine = engine;
            return this;
        }

        public SearchApiWebSearchRequestBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public SearchApiWebSearchRequestBuilder query(String query) {
            this.query = query;
            return this;
        }

        public SearchApiWebSearchRequestBuilder optionalParameters(Map<String, Object> optionalParameters) {
            this.optionalParameters = optionalParameters;
            return this;
        }

        public SearchApiWebSearchRequestBuilder additionalRequestParameters(Map<String, Object> additionalRequestParameters) {
            this.additionalRequestParameters = additionalRequestParameters;
            return this;
        }

        public SearchApiWebSearchRequest build() {
            return new SearchApiWebSearchRequest(this.engine, this.apiKey, this.query, this.optionalParameters, this.additionalRequestParameters);
        }

        public String toString() {
            return "SearchApiWebSearchRequest.SearchApiWebSearchRequestBuilder(engine=" + this.engine + ", apiKey=" + this.apiKey + ", query=" + this.query + ", optionalParameters=" + this.optionalParameters + ", additionalRequestParameters=" + this.additionalRequestParameters + ")";
        }
    }
}
