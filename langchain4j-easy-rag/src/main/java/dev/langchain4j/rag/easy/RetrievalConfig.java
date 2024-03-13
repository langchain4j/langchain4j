package dev.langchain4j.rag.easy;

public class RetrievalConfig {

    private final Integer maxResults;
    private final Double minScore;

    public RetrievalConfig(Integer maxResults, Double minScore) {
        this.maxResults = maxResults;
        this.minScore = minScore;
    }

    public Integer maxResults() {
        return maxResults;
    }

    public Double minScore() {
        return minScore;
    }
}