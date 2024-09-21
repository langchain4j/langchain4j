package dev.langchain4j.model.voyage;

public enum VoyageScoringModelName {

    RERANK_1("rerank-1"),
    RERANK_LITE_1("rerank-lite-1");

    private final String stringValue;

    VoyageScoringModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
