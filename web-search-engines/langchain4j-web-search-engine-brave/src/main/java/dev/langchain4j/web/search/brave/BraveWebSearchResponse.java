package dev.langchain4j.web.search.brave;

import lombok.Data;

import java.util.List;

@Data
class BraveWebSearchResponse {

    private Query query;
    private String type;
    private Object videos;
    private Web web;

    @Data
    public static class Query {
        private String query;
    }

    @Data
    public static class Web {
        private List<Result> results;

        @Data
        public static class Result {
            private String title;
            private String url;
            private String description;
        }
    }
}
