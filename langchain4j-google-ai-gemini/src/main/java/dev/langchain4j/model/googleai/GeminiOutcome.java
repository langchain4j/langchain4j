package dev.langchain4j.model.googleai;

enum GeminiOutcome {
    OUTCOME_UNSPECIFIED,
    OUTCOME_OK,
    OUTCOME_FAILED,
    OUTCOME_DEADLINE_EXCEEDED;

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}
