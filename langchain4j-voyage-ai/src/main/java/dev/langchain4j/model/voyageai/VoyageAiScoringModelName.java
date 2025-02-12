package dev.langchain4j.model.voyageai;

public enum VoyageAiScoringModelName {
    RERANK_1("rerank-1"),
    RERANK_LITE_1("rerank-lite-1"),

    RERANK_2("rerank-2"),
    RERANK_2_LITE("rerank-2-lite");

    private final String stringValue;

    VoyageAiScoringModelName(String stringValue) {
        this.stringValue = stringValue;
    }

    @Override
    public String toString() {
        return stringValue;
    }
}
