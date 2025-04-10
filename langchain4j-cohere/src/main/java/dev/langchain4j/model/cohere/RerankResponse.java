package dev.langchain4j.model.cohere;

import java.util.List;

class RerankResponse {

    private List<Result> results;
    private Meta meta;

    public List<Result> getResults() {
        return this.results;
    }

    public Meta getMeta() {
        return this.meta;
    }
}
