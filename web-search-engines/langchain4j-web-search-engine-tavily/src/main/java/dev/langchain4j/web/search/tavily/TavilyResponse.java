package dev.langchain4j.web.search.tavily;

import java.util.List;

class TavilyResponse {

    private String answer;
    private String query;
    private Double responseTime;
    private List<String> images;
    private List<String> followUpQuestions;
    private List<TavilySearchResult> results;

    public TavilyResponse(String answer, String query, Double responseTime, List<String> images, List<String> followUpQuestions, List<TavilySearchResult> results) {
        this.answer = answer;
        this.query = query;
        this.responseTime = responseTime;
        this.images = images;
        this.followUpQuestions = followUpQuestions;
        this.results = results;
    }

    public TavilyResponse() {
    }

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

    public static class TavilyResponseBuilder {
        private String answer;
        private String query;
        private Double responseTime;
        private List<String> images;
        private List<String> followUpQuestions;
        private List<TavilySearchResult> results;

        TavilyResponseBuilder() {
        }

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

        public TavilyResponse build() {
            return new TavilyResponse(this.answer, this.query, this.responseTime, this.images, this.followUpQuestions, this.results);
        }

        public String toString() {
            return "TavilyResponse.TavilyResponseBuilder(answer=" + this.answer + ", query=" + this.query + ", responseTime=" + this.responseTime + ", images=" + this.images + ", followUpQuestions=" + this.followUpQuestions + ", results=" + this.results + ")";
        }
    }
}
