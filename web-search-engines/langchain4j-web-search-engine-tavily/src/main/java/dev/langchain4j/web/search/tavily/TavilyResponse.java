package dev.langchain4j.web.search.tavily;

import java.util.List;
import java.util.Map;

class TavilyResponse {

    private String answer;
    private String query;
    private Double responseTime;
    private List<String> images;
    private List<String> followUpQuestions;
    private List<TavilySearchResult> results;
    private Map<String, Object> autoParameters;
    private Map<String, Object> usage;
    private String requestId;

    public TavilyResponse(
            String answer,
            String query,
            Double responseTime,
            List<String> images,
            List<String> followUpQuestions,
            List<TavilySearchResult> results,
            Map<String, Object> autoParameters,
            Map<String, Object> usage,
            String requestId) {
        this.answer = answer;
        this.query = query;
        this.responseTime = responseTime;
        this.images = images;
        this.followUpQuestions = followUpQuestions;
        this.results = results;
        this.autoParameters = autoParameters;
        this.usage = usage;
        this.requestId = requestId;
    }

    public TavilyResponse() {}

    public static TavilyResponseBuilder builder() {
        return new TavilyResponseBuilder();
    }

    public String getAnswer() {
        return this.answer;
    }

    public String getQuery() {
        return this.query;
    }

    public Double getResponseTime() {
        return this.responseTime;
    }

    public List<String> getImages() {
        return this.images;
    }

    public List<String> getFollowUpQuestions() {
        return this.followUpQuestions;
    }

    public List<TavilySearchResult> getResults() {
        return this.results;
    }

    public Map<String, Object> getAutoParameters() {
        return this.autoParameters;
    }

    public Map<String, Object> getUsage() {
        return this.usage;
    }

    public String getRequestId() {
        return this.requestId;
    }

    public static class TavilyResponseBuilder {
        private String answer;
        private String query;
        private Double responseTime;
        private List<String> images;
        private List<String> followUpQuestions;
        private List<TavilySearchResult> results;
        private Map<String, Object> autoParameters;
        private Map<String, Object> usage;
        private String requestId;

        TavilyResponseBuilder() {}

        public TavilyResponseBuilder answer(String answer) {
            this.answer = answer;
            return this;
        }

        public TavilyResponseBuilder query(String query) {
            this.query = query;
            return this;
        }

        public TavilyResponseBuilder responseTime(Double responseTime) {
            this.responseTime = responseTime;
            return this;
        }

        public TavilyResponseBuilder images(List<String> images) {
            this.images = images;
            return this;
        }

        public TavilyResponseBuilder followUpQuestions(List<String> followUpQuestions) {
            this.followUpQuestions = followUpQuestions;
            return this;
        }

        public TavilyResponseBuilder results(List<TavilySearchResult> results) {
            this.results = results;
            return this;
        }

        public TavilyResponseBuilder autoParameters(Map<String, Object> autoParameters) {
            this.autoParameters = autoParameters;
            return this;
        }

        public TavilyResponseBuilder usage(Map<String, Object> usage) {
            this.usage = usage;
            return this;
        }

        public TavilyResponseBuilder requestId(String requestId) {
            this.requestId = requestId;
            return this;
        }

        public TavilyResponse build() {
            return new TavilyResponse(
                    this.answer,
                    this.query,
                    this.responseTime,
                    this.images,
                    this.followUpQuestions,
                    this.results,
                    this.autoParameters,
                    this.usage,
                    this.requestId);
        }

        public String toString() {
            return "TavilyResponse.TavilyResponseBuilder(answer=" + this.answer + ", query=" + this.query
                    + ", responseTime=" + this.responseTime + ", images=" + this.images + ", followUpQuestions="
                    + this.followUpQuestions + ", results=" + this.results + ", autoParameters=" + this.autoParameters
                    + ", usage=" + this.usage + ", requestId=" + this.requestId + ")";
        }
    }
}
